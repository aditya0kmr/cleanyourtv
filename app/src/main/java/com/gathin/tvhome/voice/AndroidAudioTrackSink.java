package com.gathin.tvhome.voice;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

/**
 * Production implementation of AudioSink using android.media.AudioTrack in MODE_STREAM.
 * Explicitly binds to USAGE_MEDIA / CONTENT_TYPE_MUSIC to route through STREAM_MUSIC (HDMI sink).
 */
public class AndroidAudioTrackSink implements AudioSink {
    private static final String TAG = "VoiceAudioSink";

    private AudioTrack audioTrack;
    private volatile boolean isPlaying = false;

    @Override
    public synchronized void init(int sampleRate, int channels, int encoding, int minBufferSize) throws Exception {
        release();

        int channelMask = (channels == 2) ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
        int pcmEncoding = (encoding == 16) ? AudioFormat.ENCODING_PCM_16BIT : AudioFormat.ENCODING_PCM_8BIT;

        int systemMinBuffer = AudioTrack.getMinBufferSize(sampleRate, channelMask, pcmEncoding);
        int actualBufferSize = Math.max(systemMinBuffer * 2, Math.max(minBufferSize, 8192));

        // Explicitly use USAGE_MEDIA and CONTENT_TYPE_MUSIC.
        // Dumpsys confirmed STREAM_MUSIC routes directly to Devices: hdmi at volume 15.
        // We strictly avoid CONTENT_TYPE_SPEECH / STREAM_TTS which routed to speaker.
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(pcmEncoding)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();

        audioTrack = new AudioTrack(
                attrs,
                format,
                actualBufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
        );

        if (audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            int state = audioTrack.getState();
            release();
            throw new IllegalStateException("AudioTrack failed initialization, state=" + state);
        }

        isPlaying = false;
        Log.i(TAG, "AudioTrack initialized: " + sampleRate + "Hz, ch=" + channels + ", buf=" + actualBufferSize);
    }

    @Override
    public synchronized int write(byte[] data, int offset, int size) {
        if (audioTrack == null || audioTrack.getState() != AudioTrack.STATE_INITIALIZED) {
            return -1;
        }
        return audioTrack.write(data, offset, size);
    }

    @Override
    public synchronized void play() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            if (audioTrack.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                audioTrack.play();
                isPlaying = true;
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (audioTrack != null && audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
            try {
                if (audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                    audioTrack.stop();
                    audioTrack.flush();
                }
            } catch (Exception e) {
                Log.w(TAG, "Error stopping AudioTrack: " + e.getMessage());
            } finally {
                isPlaying = false;
            }
        }
    }

    @Override
    public synchronized void release() {
        isPlaying = false;
        if (audioTrack != null) {
            try {
                if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED) {
                    audioTrack.stop();
                    audioTrack.flush();
                }
            } catch (Exception ignored) {
            } finally {
                try {
                    audioTrack.release();
                } catch (Exception ignored) {}
                audioTrack = null;
            }
        }
    }

    @Override
    public synchronized boolean isPlaying() {
        return isPlaying && audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING;
    }
}
