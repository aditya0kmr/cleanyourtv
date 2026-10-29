package com.aether.launcher.iptv;

import android.content.Context;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.VideoView;
import com.aether.launcher.R;
import com.aether.launcher.audio.CyberSoundSynth;
import com.aether.launcher.hud.HologramTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CyberStreamDeck {

    public interface OnCloseListener {
        void onClose();
    }

    private final View rootView;
    private final VideoView videoPlayer;
    private final TextView statusText;
    private final TextView channelNameText;
    private final EditText urlInput;
    private final List<StreamChannel> channels = new ArrayList<>();
    private int currentChannelIdx = 0;
    private OnCloseListener closeListener;

    public CyberStreamDeck(View root) {
        this.rootView = root;
        this.videoPlayer = root.findViewById(R.id.video_player);
        this.statusText = root.findViewById(R.id.stream_status);
        this.channelNameText = root.findViewById(R.id.channel_name);
        this.urlInput = root.findViewById(R.id.input_stream_url);

        initDefaultChannels();
        initControls();
    }

    private void initDefaultChannels() {
        // High reliability public HLS streams
        channels.add(new StreamChannel("NASA TV LIVE", "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8", "SPACE"));
        channels.add(new StreamChannel("FRANCE 24 NEWS", "https://stream.france24.com/hls/live/2037989/f24_en/master.m3u8", "NEWS"));
        channels.add(new StreamChannel("RED BULL ACTION TV", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8", "SPORTS"));
        channels.add(new StreamChannel("EURONEWS GLOBAL", "https://euronews-euronews-world-1-au.samsung.wurl.tv/playlist.m3u8", "GLOBAL"));
    }

    private void initControls() {
        rootView.findViewById(R.id.btn_close_stream).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            stop();
            if (closeListener != null) closeListener.onClose();
        });

        rootView.findViewById(R.id.btn_prev_ch).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            prevChannel();
        });

        rootView.findViewById(R.id.btn_next_ch).setOnClickListener(v -> {
            CyberSoundSynth.get().playChirp();
            nextChannel();
        });

        rootView.findViewById(R.id.btn_load_url).setOnClickListener(v -> {
            CyberSoundSynth.get().playPowerUp();
            String url = urlInput.getText().toString().trim();
            if (!url.isEmpty()) {
                playUrl(url, "CUSTOM STREAM");
            }
        });

        videoPlayer.setOnPreparedListener(mp -> {
            statusText.setText("SIGNAL // 1080p ONLINE");
            statusText.setTextColor(0xFF00FF66);
            videoPlayer.start();
        });

        videoPlayer.setOnErrorListener((mp, what, extra) -> {
            statusText.setText("SIGNAL // OFFLINE / BUFFER FAULT");
            statusText.setTextColor(0xFFFF2A6D);
            return true;
        });
    }

    public void setOnCloseListener(OnCloseListener listener) {
        this.closeListener = listener;
    }

    public void start() {
        tuneCurrentChannel();
    }

    public void stop() {
        try {
            if (videoPlayer.isPlaying()) {
                videoPlayer.stopPlayback();
            }
        } catch (Exception ignored) {}
    }

    private void prevChannel() {
        if (channels.isEmpty()) return;
        currentChannelIdx = (currentChannelIdx - 1 + channels.size()) % channels.size();
        tuneCurrentChannel();
    }

    private void nextChannel() {
        if (channels.isEmpty()) return;
        currentChannelIdx = (currentChannelIdx + 1) % channels.size();
        tuneCurrentChannel();
    }

    private void tuneCurrentChannel() {
        if (channels.isEmpty()) return;
        StreamChannel ch = channels.get(currentChannelIdx);
        playUrl(ch.getStreamUrl(), "CH " + String.format(Locale.US, "%02d", currentChannelIdx + 1) + " // " + ch.getName());
    }

    public void playUrl(String url, String title) {
        channelNameText.setText(title);
        statusText.setText("SIGNAL // TUNING FEED...");
        statusText.setTextColor(HologramTheme.get().getPrimary());

        try {
            videoPlayer.stopPlayback();
            videoPlayer.setVideoURI(Uri.parse(url));
        } catch (Exception e) {
            statusText.setText("SIGNAL // ERROR: " + e.getMessage());
            statusText.setTextColor(0xFFFF2A6D);
        }
    }
}
