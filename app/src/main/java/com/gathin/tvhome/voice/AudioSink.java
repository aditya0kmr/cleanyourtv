package com.gathin.tvhome.voice;

/**
 * Abstraction for audio output device/track.
 * Allows decoupling AudioTrack lifecycle management from framework dependencies for testing.
 */
public interface AudioSink {
    /**
     * Initializes the sink with requested audio parameters.
     * Must configure USAGE_MEDIA in production.
     */
    void init(int sampleRate, int channels, int encoding, int minBufferSize) throws Exception;

    /**
     * Writes raw PCM bytes into the audio sink buffer.
     * Returns number of bytes written, or negative error code.
     */
    int write(byte[] data, int offset, int size);

    /**
     * Starts playback.
     */
    void play();

    /**
     * Stops playback and flushes pending audio.
     */
    void stop();

    /**
     * Releases system audio hardware resources immediately.
     */
    void release();

    /**
     * Returns true if currently in PLAYSTATE_PLAYING.
     */
    boolean isPlaying();
}
