package com.aether.launcher.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CyberSoundSynth {

    private static CyberSoundSynth instance;
    private final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
    private boolean enabled = true;
    private static final int SAMPLE_RATE = 22050;

    private CyberSoundSynth() {}

    public static synchronized CyberSoundSynth get() {
        if (instance == null) {
            instance = new CyberSoundSynth();
        }
        return instance;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void playDataClick() {
        if (!enabled) return;
        audioExecutor.execute(() -> {
            int durationMs = 15;
            int numSamples = (SAMPLE_RATE * durationMs) / 1000;
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double freq = 2400.0;
                double envelope = 1.0 - ((double) i / numSamples);
                double sample = Math.sin(2.0 * Math.PI * freq * t) * envelope;
                buffer[i] = (short) (sample * 14000);
            }
            playPcm(buffer);
        });
    }

    public void playChirp() {
        if (!enabled) return;
        audioExecutor.execute(() -> {
            int durationMs = 60;
            int numSamples = (SAMPLE_RATE * durationMs) / 1000;
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double progress = (double) i / numSamples;
                double freq = 900.0 + (progress * 1600.0);
                double envelope = Math.sin(Math.PI * progress);
                double sample = Math.sin(2.0 * Math.PI * freq * t) * envelope;
                buffer[i] = (short) (sample * 18000);
            }
            playPcm(buffer);
        });
    }

    public void playPowerUp() {
        if (!enabled) return;
        audioExecutor.execute(() -> {
            int durationMs = 280;
            int numSamples = (SAMPLE_RATE * durationMs) / 1000;
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double progress = (double) i / numSamples;
                double freq = 80.0 + (Math.pow(progress, 2.0) * 520.0);
                double envelope = Math.sin(Math.PI * Math.sqrt(progress));
                double sample = Math.sin(2.0 * Math.PI * freq * t) * envelope;
                // Add resonant harmonic
                sample += 0.3 * Math.sin(4.0 * Math.PI * freq * t) * envelope;
                buffer[i] = (short) (Math.min(1.0, Math.max(-1.0, sample)) * 20000);
            }
            playPcm(buffer);
        });
    }

    public void playWarp() {
        if (!enabled) return;
        audioExecutor.execute(() -> {
            int durationMs = 180;
            int numSamples = (SAMPLE_RATE * durationMs) / 1000;
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double progress = (double) i / numSamples;
                double freq = 1400.0 - (progress * 900.0);
                double envelope = 1.0 - progress;
                double sample = (Math.sin(2.0 * Math.PI * freq * t) > 0 ? 0.7 : -0.7) * envelope;
                buffer[i] = (short) (sample * 16000);
            }
            playPcm(buffer);
        });
    }

    public void playLaser() {
        if (!enabled) return;
        audioExecutor.execute(() -> {
            int durationMs = 90;
            int numSamples = (SAMPLE_RATE * durationMs) / 1000;
            short[] buffer = new short[numSamples];
            for (int i = 0; i < numSamples; i++) {
                double t = (double) i / SAMPLE_RATE;
                double progress = (double) i / numSamples;
                double freq = 2200.0 * Math.exp(-progress * 4.0);
                double envelope = 1.0 - progress;
                double sample = Math.sin(2.0 * Math.PI * freq * t) * envelope;
                buffer[i] = (short) (sample * 19000);
            }
            playPcm(buffer);
        });
    }

    private void playPcm(short[] pcm) {
        AudioTrack track = null;
        try {
            int minBuffer = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
            );
            int bufferSize = Math.max(pcm.length * 2, minBuffer);

            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            AudioFormat format = new AudioFormat.Builder()
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .build();

            track = new AudioTrack(
                    attributes,
                    format,
                    bufferSize,
                    AudioTrack.MODE_STATIC,
                    0
            );

            track.write(pcm, 0, pcm.length);
            track.play();
            // Wait slightly for playback to complete
            Thread.sleep((pcm.length * 1000L) / SAMPLE_RATE + 20);
        } catch (Exception ignored) {
        } finally {
            if (track != null) {
                try {
                    track.stop();
                    track.release();
                } catch (Exception ignored) {}
            }
        }
    }
}
