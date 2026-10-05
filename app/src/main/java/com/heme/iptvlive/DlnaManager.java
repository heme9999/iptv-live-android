package com.heme.iptvlive;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public final class DlnaManager {
    private static final String TAG = "DlnaManager";
    private static final String MULTICAST_IP = "239.255.255.250";
    private static final int MULTICAST_PORT = 1900;
    private static final String AV_TRANSPORT_SERVICE = "urn:schemas-upnp-org:service:AVTransport:1";

    public interface DiscoveryListener {
        void onDeviceFound(DlnaDevice device);
        void onDiscoveryFinished(List<DlnaDevice> devices);
    }

    public interface ActionCallback {
        void onSuccess();
        void onError(String message);
    }

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final List<DlnaDevice> discoveredDevices = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> discoveredLocations = Collections.synchronizedSet(new HashSet<>());
    private boolean isSearching = false;
    private WifiManager.MulticastLock multicastLock;
    private DlnaDevice currentCastDevice;

    public DlnaManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public List<DlnaDevice> getDiscoveredDevices() {
        return new ArrayList<>(discoveredDevices);
    }

    public DlnaDevice getCurrentCastDevice() {
        return currentCastDevice;
    }

    public void startDiscovery(DiscoveryListener listener) {
        if (isSearching) return;
        isSearching = true;
        discoveredDevices.clear();
        discoveredLocations.clear();

        acquireMulticastLock();

        executor.execute(() -> {
            MulticastSocket socket = null;
            try {
                socket = new MulticastSocket(null);
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(0));
                socket.setTimeToLive(4);
                socket.setSoTimeout(4000);

                InetAddress group = InetAddress.getByName(MULTICAST_IP);

                String[] targets = {
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "ssdp:all"
                };

                for (String st : targets) {
                    String query = "M-SEARCH * HTTP/1.1\r\n" +
                            "HOST: " + MULTICAST_IP + ":" + MULTICAST_PORT + "\r\n" +
                            "MAN: \"ssdp:discover\"\r\n" +
                            "MX: 3\r\n" +
                            "ST: " + st + "\r\n\r\n";
                    byte[] data = query.getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(data, data.length, group, MULTICAST_PORT);
                    socket.send(packet);
                }

                long endTime = System.currentTimeMillis() + 4500;
                byte[] buf = new byte[8192];
                while (System.currentTimeMillis() < endTime) {
                    try {
                        DatagramPacket response = new DatagramPacket(buf, buf.length);
                        socket.receive(response);
                        String content = new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8);
                        String location = parseHeader(content, "LOCATION");
                        if (location != null && !location.isEmpty() && discoveredLocations.add(location)) {
                            fetchAndParseDevice(location, listener);
                        }
                    } catch (Exception ignore) {
                        // socket timeout or read next
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Discovery error: " + e.getMessage());
            } finally {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                }
                releaseMulticastLock();
                isSearching = false;
                if (listener != null) {
                    mainHandler.post(() -> listener.onDiscoveryFinished(new ArrayList<>(discoveredDevices)));
                }
            }
        });
    }

    private void fetchAndParseDevice(String locationUrl, DiscoveryListener listener) {
        executor.execute(() -> {
            try {
                URL url = new URL(locationUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(4000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "DLNADOC/1.50 UPnP/1.0");

                int code = conn.getResponseCode();
                if (code == 200) {
                    try (InputStream in = conn.getInputStream();
                         BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line).append("\n");
                        }
                        DlnaDevice device = parseDeviceXml(locationUrl, url.getHost(), sb.toString());
                        if (device != null) {
                            discoveredDevices.add(device);
                            if (listener != null) {
                                mainHandler.post(() -> listener.onDeviceFound(device));
                            }
                        }
                    }
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Fetch device xml error: " + e.getMessage());
            }
        });
    }

    private DlnaDevice parseDeviceXml(String locationUrl, String host, String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            String friendlyName = getElementText(doc, "friendlyName");
            if (friendlyName == null || friendlyName.isEmpty()) {
                friendlyName = "智能电视 (" + host + ")";
            }

            NodeList serviceList = doc.getElementsByTagName("service");
            String controlUrl = null;
            String serviceType = AV_TRANSPORT_SERVICE;

            for (int i = 0; i < serviceList.getLength(); i++) {
                Element service = (Element) serviceList.item(i);
                String st = getChildText(service, "serviceType");
                if (st != null && st.contains("AVTransport")) {
                    serviceType = st;
                    controlUrl = getChildText(service, "controlURL");
                    break;
                }
            }

            if (controlUrl != null) {
                controlUrl = resolveUrl(locationUrl, controlUrl);
                return new DlnaDevice(friendlyName, host, locationUrl, controlUrl, serviceType);
            }
        } catch (Exception e) {
            Log.w(TAG, "Parse device xml error: " + e.getMessage());
        }
        return null;
    }

    private String resolveUrl(String baseUrl, String relativeUrl) {
        try {
            return new URL(new URL(baseUrl), relativeUrl).toString();
        } catch (Exception e) {
            return relativeUrl;
        }
    }

    private String getElementText(Document doc, String tagName) {
        NodeList list = doc.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0).getFirstChild() != null) {
            return list.item(0).getFirstChild().getNodeValue();
        }
        return null;
    }

    private String getChildText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0).getFirstChild() != null) {
            return list.item(0).getFirstChild().getNodeValue();
        }
        return null;
    }

    private String parseHeader(String response, String headerName) {
        String[] lines = response.split("\r\n");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String key = line.substring(0, idx).trim();
                if (key.equalsIgnoreCase(headerName)) {
                    return line.substring(idx + 1).trim();
                }
            }
        }
        return null;
    }

    public void cast(DlnaDevice device, String mediaUrl, String title, ActionCallback callback) {
        if (device == null || mediaUrl == null || mediaUrl.isEmpty()) {
            if (callback != null) callback.onError("设备或播放地址为空");
            return;
        }

        executor.execute(() -> {
            try {
                // 1. SetAVTransportURI
                String metaData = "&lt;DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
                        "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\"&gt;" +
                        "&lt;item id=\"0\" parentID=\"-1\" restricted=\"1\"&gt;" +
                        "&lt;dc:title&gt;" + escapeXml(title != null ? title : "直播频道") + "&lt;/dc:title&gt;" +
                        "&lt;upnp:class&gt;object.item.videoItem&lt;/upnp:class&gt;" +
                        "&lt;res protocolInfo=\"http-get:*:video/mp4:*\"&gt;" + escapeXml(mediaUrl) + "&lt;/res&gt;" +
                        "&lt;/item&gt;&lt;/DIDL-Lite&gt;";

                String setUriSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
                        "  <s:Body>\n" +
                        "    <u:SetAVTransportURI xmlns:u=\"" + device.serviceType + "\">\n" +
                        "      <InstanceID>0</InstanceID>\n" +
                        "      <CurrentURI>" + escapeXml(mediaUrl) + "</CurrentURI>\n" +
                        "      <CurrentURIMetaData>" + metaData + "</CurrentURIMetaData>\n" +
                        "    </u:SetAVTransportURI>\n" +
                        "  </s:Body>\n" +
                        "</s:Envelope>";

                sendSoapAction(device.controlUrl, device.serviceType, "SetAVTransportURI", setUriSoap);

                // 2. Play
                String playSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
                        "  <s:Body>\n" +
                        "    <u:Play xmlns:u=\"" + device.serviceType + "\">\n" +
                        "      <InstanceID>0</InstanceID>\n" +
                        "      <Speed>1</Speed>\n" +
                        "    </u:Play>\n" +
                        "  </s:Body>\n" +
                        "</s:Envelope>";

                sendSoapAction(device.controlUrl, device.serviceType, "Play", playSoap);

                currentCastDevice = device;
                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                Log.e(TAG, "Cast failed: " + e.getMessage(), e);
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("投屏失败：" + e.getMessage()));
                }
            }
        });
    }

    public void stop(DlnaDevice device, ActionCallback callback) {
        if (device == null) return;
        executor.execute(() -> {
            try {
                String stopSoap = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                        "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\n" +
                        "  <s:Body>\n" +
                        "    <u:Stop xmlns:u=\"" + device.serviceType + "\">\n" +
                        "      <InstanceID>0</InstanceID>\n" +
                        "    </u:Stop>\n" +
                        "  </s:Body>\n" +
                        "</s:Envelope>";

                sendSoapAction(device.controlUrl, device.serviceType, "Stop", stopSoap);
                if (currentCastDevice != null && currentCastDevice.equals(device)) {
                    currentCastDevice = null;
                }
                if (callback != null) {
                    mainHandler.post(callback::onSuccess);
                }
            } catch (Exception e) {
                if (callback != null) {
                    mainHandler.post(() -> callback.onError("停止失败：" + e.getMessage()));
                }
            }
        });
    }

    private void sendSoapAction(String controlUrl, String serviceType, String action, String soapBody) throws Exception {
        URL url = new URL(controlUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(5000);
        conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        conn.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + action + "\"");
        conn.setRequestProperty("User-Agent", "DLNADOC/1.50 UPnP/1.0");

        byte[] bytes = soapBody.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(bytes.length);

        try (OutputStream out = conn.getOutputStream()) {
            out.write(bytes);
            out.flush();
        }

        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new Exception("HTTP " + code + " " + conn.getResponseMessage());
        }
        conn.disconnect();
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private void acquireMulticastLock() {
        try {
            if (multicastLock == null) {
                WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
                if (wm != null) {
                    multicastLock = wm.createMulticastLock("iptv_dlna_lock");
                    multicastLock.setReferenceCounted(false);
                }
            }
            if (multicastLock != null && !multicastLock.isHeld()) {
                multicastLock.acquire();
            }
        } catch (Exception e) {
            Log.w(TAG, "MulticastLock acquire error: " + e.getMessage());
        }
    }

    private void releaseMulticastLock() {
        try {
            if (multicastLock != null && multicastLock.isHeld()) {
                multicastLock.release();
            }
        } catch (Exception ignore) {}
    }

    public void release() {
        releaseMulticastLock();
        executor.shutdownNow();
    }
}
