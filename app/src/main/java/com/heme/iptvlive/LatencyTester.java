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
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(4_000);
            connection.setReadTimeout(4_000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "IPTV-Live-Android");
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setRequestProperty("Cache-Control", "no-cache");
            int code = connection.getResponseCode();
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            return code >= 200 && code < 500 ? elapsed : -1L;
        } catch (Exception ignored) {
            return -1L;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    void close() { executor.shutdownNow(); }
}
