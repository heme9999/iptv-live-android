package com.heme.iptvlive;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class M3uParser {
    private static final Pattern GROUP = Pattern.compile("group-title=\\\"([^\\\"]*)\\\"");

    static List<Channel> fromAssets(Context context) throws Exception {
        List<Channel> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("channels.m3u")))) {
            String line;
            String name = null;
            String group = "其他";
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#EXTINF:")) {
                    int comma = line.lastIndexOf(',');
                    name = comma >= 0 ? line.substring(comma + 1).trim() : "未命名频道";
                    Matcher matcher = GROUP.matcher(line);
                    group = matcher.find() ? matcher.group(1) : "其他";
                } else if (!line.isEmpty() && !line.startsWith("#") && name != null) {
                    result.add(new Channel(name, group, line));
                    name = null;
                }
            }
        }
        return result;
    }
}

