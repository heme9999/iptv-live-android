package com.heme.iptvlive;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class M3uParser {
    private static final Pattern GROUP = Pattern.compile("group-title=\\\"([^\\\"]*)\\\"");

    static List<Channel> parse(BufferedReader reader) throws Exception {
        List<Channel> result = new ArrayList<>();
        String line;
        String name = null;
        String group = "其他";
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("#EXTINF:")) {
                int comma = line.lastIndexOf(',');
                name = comma >= 0 ? line.substring(comma + 1).trim() : "未命名频道";
                Matcher matcher = GROUP.matcher(line);
                group = matcher.find() ? matcher.group(1).trim() : "其他";
                if (group.isEmpty()) group = "其他";
            } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                result.add(new Channel(name, group, line));
                name = null;
            }
        }
        return result;
    }

    static List<Channel> fromAssets(Context context) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("channels.m3u"), StandardCharsets.UTF_8))) {
            return parse(reader);
        }
    }

    static List<Channel> fromUrl(String urlString) throws Exception {
        URL url = new URL(urlString.trim());
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        int responseCode = conn.getResponseCode();
        if (responseCode < 200 || responseCode >= 300) {
            throw new Exception("HTTP 响应错误：" + responseCode);
        }
        try (InputStream in = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<Channel> list = parse(reader);
            if (list.isEmpty()) {
                throw new Exception("未从该链接中解析到有效 M3U 频道");
            }
            return list;
        } finally {
            conn.disconnect();
        }
    }

    static List<Channel> fromString(String content) throws Exception {
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            return parse(reader);
        }
    }
}
