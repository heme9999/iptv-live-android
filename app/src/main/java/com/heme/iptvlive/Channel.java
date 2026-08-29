package com.heme.iptvlive;

final class Channel {
    final String name;
    final String group;
    final String url;
    volatile long latencyMs = -2L;

    Channel(String name, String group, String url) {
        this.name = name;
        this.group = group;
        this.url = url;
    }
}
