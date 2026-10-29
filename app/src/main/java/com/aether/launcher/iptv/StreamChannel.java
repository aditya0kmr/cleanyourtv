package com.aether.launcher.iptv;

public class StreamChannel {

    private final String name;
    private final String streamUrl;
    private final String genre;

    public StreamChannel(String name, String streamUrl, String genre) {
        this.name = name;
        this.streamUrl = streamUrl;
        this.genre = genre;
    }

    public String getName() { return name; }
    public String getStreamUrl() { return streamUrl; }
    public String getGenre() { return genre; }
}
