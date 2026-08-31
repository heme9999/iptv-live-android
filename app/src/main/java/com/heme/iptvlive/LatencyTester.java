package com.heme.iptvlive;

import android.os.Handler;
import android.os.Looper;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class LatencyTester {
    interface Listener { void onMeasured(Channel channel); }
    private final ExecutorService executor = Executors.newFixedThreadPool(6);
    private final Handler main = new Handler(Looper.getMainLooper());

    void measureAll(List<Channel> channels, Listener listener) {
        for (Channel channel : channels) executor.execute(() -> {
            channel.latencyMs = measure(channel.url);
            main.post(() -> listener.onMeasured(channel));
        });
    }

    private long measure(String address) {
        HttpURLConnection connection = null;
        long started = System.nanoTime();
        try {
            String currentUrl = address;
            int redirects = 0;
            while (redirects < 5) {
                connection = (HttpURLConnection) new URL(currentUrl).openConnection();
                connection.setConnectTimeout(3_500);
                connection.setReadTimeout(3_500);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 APTV/1.0");
                connection.setRequestProperty("Cache-Control", "no-cache");
                int code = connection.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                    String loc = connection.getHeaderField("Location");
                    if (loc != null && !loc.isEmpty()) {
                        connection.disconnect();
                        currentUrl = loc;
                        redirects++;
                        continue;
                    }
                }
                long elapsed = (System.nanoTime() - started) / 1_000_000L;
                return (code >= 200 && code < 400) ? elapsed : -1L;
            }
            return -1L;
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    void close() { executor.shutdownNow(); }
}
