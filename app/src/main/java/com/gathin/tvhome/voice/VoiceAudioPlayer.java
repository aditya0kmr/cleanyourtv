package com.gathin.tvhome.voice;

import android.content.Context;
import android.util.Log;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Dedicated in-app Voice Audio Player for Sovereign Glass Terminal.
 * Receives raw PCM binary WebSocket frames and streams them directly
 * to an AudioSink (AudioTrack STREAM_MUSIC) on a dedicated worker thread.
 */
public class VoiceAudioPlayer {
    private static final String TAG = "VoiceAudioPlayer";

    private static void logI(String tag, String msg) {
        try {
            Log.i(tag, msg);
        } catch (Throwable t) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    private static void logW(String tag, String msg) {
        try {
            Log.w(tag, msg);
        } catch (Throwable t) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    private static void logE(String tag, String msg) {
        try {
            Log.e(tag, msg);
        } catch (Throwable t) {
            System.err.println("[" + tag + "] " + msg);
        }
    }

    private static void logD(String tag, String msg) {
        try {
            Log.d(tag, msg);
        } catch (Throwable t) {
            System.out.println("[" + tag + "] " + msg);
        }
    }

    // Bounded chunk queue capacity to eliminate memory bloat
    private static final int MAX_QUEUE_CHUNKS = 100;
    private static final byte[] POISON_PILL = new byte[0];

    public interface PlaybackListener {
        void onPlaybackStarted(String messageId);
        void onPlaybackCompleted(String messageId);
        void onPlaybackCancelled(String messageId, String reason);
        void onPlaybackError(String messageId, String error);
    }

    private final AudioSink audioSink;
    private PlaybackListener listener;

    private final Object sessionLock = new Object();
    private String activeMessageId = null;
    private int expectedTotalBytes = 0;
    private int receivedBytes = 0;
    private long expiresAt = 0;
    private boolean isCompleting = false;

    private final BlockingQueue<byte[]> chunkQueue = new LinkedBlockingQueue<>(MAX_QUEUE_CHUNKS);
    private Thread playbackThread;
    private final AtomicBoolean isWorkerRunning = new AtomicBoolean(false);

    public VoiceAudioPlayer(Context context) {
        this(new AndroidAudioTrackSink());
    }

    public VoiceAudioPlayer(AudioSink sink) {
        this.audioSink = sink;
    }

    public void setListener(PlaybackListener listener) {
        this.listener = listener;
    }

    /**
     * Handles control JSON messages (voice_audio_start, voice_audio_complete, voice_audio_cancel, voice_audio_error).
     */
    public void handleControlMessage(String jsonStr) {
        if (jsonStr == null || jsonStr.trim().isEmpty()) return;

        long nowSeconds = System.currentTimeMillis() / 1000;

        // Try start message
        VoiceAudioProtocol.StartHeader startHeader = VoiceAudioProtocol.parseStartMessage(jsonStr, nowSeconds);
        if (startHeader != null) {
            handleStart(startHeader);
            return;
        }

        // Try control action (complete, cancel, error)
        VoiceAudioProtocol.ControlAction action = VoiceAudioProtocol.parseControlAction(jsonStr);
        if (action != null) {
            handleAction(action);
        }
    }

    private void handleStart(VoiceAudioProtocol.StartHeader header) {
        synchronized (sessionLock) {
            // Ignore duplicate start messages for the same active messageId
            if (activeMessageId != null && activeMessageId.equals(header.messageId)) {
                logD(TAG, "Ignoring duplicate voice_audio_start for messageId=" + header.messageId);
                return;
            }

            // Cancel any active utterance before replacing with the new stream
            if (activeMessageId != null) {
                cancelCurrentSessionLocked("replaced_by_new_utterance");
            }

            try {
                // Initialize sink with audio parameters
                audioSink.init(header.sampleRate, header.channels, 16, 8192);
            } catch (Exception e) {
                logE(TAG, "Failed to initialize AudioSink: " + e.getMessage());
                if (listener != null) listener.onPlaybackError(header.messageId, "init_failed: " + e.getMessage());
                return;
            }

            activeMessageId = header.messageId;
            expectedTotalBytes = header.totalBytes;
            receivedBytes = 0;
            expiresAt = header.expiresAt;
            isCompleting = false;
            chunkQueue.clear();

            startPlaybackWorkerLocked();

            if (listener != null) {
                listener.onPlaybackStarted(activeMessageId);
            }
            logI(TAG, "Started voice session: id=" + activeMessageId + " bytes=" + expectedTotalBytes);
        }
    }

    private void handleAction(VoiceAudioProtocol.ControlAction action) {
        synchronized (sessionLock) {
            if (activeMessageId == null || !activeMessageId.equals(action.messageId)) {
                // Mismatched or stale message_id, drop safely
                logD(TAG, "Dropping control action " + action.type + " for inactive id=" + action.messageId);
                return;
            }

            if (VoiceAudioProtocol.TYPE_COMPLETE.equals(action.type)) {
                isCompleting = true;
                // Signal worker that stream is finished
                chunkQueue.offer(POISON_PILL);
                logI(TAG, "Marked session completing: id=" + activeMessageId);
            } else if (VoiceAudioProtocol.TYPE_CANCEL.equals(action.type)) {
                cancelCurrentSessionLocked(action.reason != null ? action.reason : "cancelled");
            } else if (VoiceAudioProtocol.TYPE_ERROR.equals(action.type)) {
                cancelCurrentSessionLocked("server_error: " + action.reason);
            }
        }
    }

    /**
     * Handles incoming raw PCM binary frames.
     */
    public void handleBinaryFrame(byte[] payload) {
        if (payload == null || payload.length == 0) return;

        synchronized (sessionLock) {
            if (activeMessageId == null) {
                // No active session, drop binary frame safely
                return;
            }

            long nowSeconds = System.currentTimeMillis() / 1000;
            if (expiresAt > 0 && nowSeconds >= expiresAt) {
                cancelCurrentSessionLocked("session_expired");
                return;
            }

            if (receivedBytes >= expectedTotalBytes) {
                // Excess data beyond total_bytes, drop
                logW(TAG, "Dropping excess binary bytes beyond expected total: " + expectedTotalBytes);
                return;
            }

            int toAccept = Math.min(payload.length, expectedTotalBytes - receivedBytes);
            byte[] chunkToQueue;
            if (toAccept == payload.length) {
                chunkToQueue = payload;
            } else {
                chunkToQueue = new byte[toAccept];
                System.arraycopy(payload, 0, chunkToQueue, 0, toAccept);
            }

            receivedBytes += toAccept;

            boolean queued = chunkQueue.offer(chunkToQueue);
            if (!queued) {
                logW(TAG, "Chunk queue full, dropping chunk for id=" + activeMessageId);
            }
        }
    }

    private void startPlaybackWorkerLocked() {
        stopPlaybackWorkerLocked();
        isWorkerRunning.set(true);

        playbackThread = new Thread(this::playbackLoop, "VoiceAudioPlayer-Worker");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void playbackLoop() {
        boolean startedPlay = false;
        String currentId = null;

        while (isWorkerRunning.get()) {
            try {
                byte[] chunk = chunkQueue.poll(200, TimeUnit.MILLISECONDS);
                if (chunk == null) {
                    synchronized (sessionLock) {
                        if (isCompleting && chunkQueue.isEmpty()) {
                            break;
                        }
                    }
                    continue;
                }

                if (chunk == POISON_PILL) {
                    break;
                }

                synchronized (sessionLock) {
                    currentId = activeMessageId;
                }

                if (!startedPlay) {
                    audioSink.play();
                    startedPlay = true;
                }

                int written = 0;
                while (written < chunk.length && isWorkerRunning.get()) {
                    int w = audioSink.write(chunk, written, chunk.length - written);
                    if (w < 0) {
                        logE(TAG, "AudioSink write error: " + w);
                        break;
                    }
                    written += w;
                }
            } catch (InterruptedException ie) {
                break;
            } catch (Exception e) {
                logE(TAG, "Error in playback loop: " + e.getMessage());
                break;
            }
        }

        // Clean termination upon stream completion
        synchronized (sessionLock) {
            String finishedId = activeMessageId;
            if (isWorkerRunning.get() && isCompleting) {
                try {
                    // Small grace period for hardware buffer to drain
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {}

                audioSink.stop();
                audioSink.release();
                activeMessageId = null;
                isCompleting = false;

                if (listener != null && finishedId != null) {
                    listener.onPlaybackCompleted(finishedId);
                }
                logI(TAG, "Voice playback completed cleanly for id=" + finishedId);
            }
        }
    }

    private void cancelCurrentSessionLocked(String reason) {
        String cancelledId = activeMessageId;
        activeMessageId = null;
        isCompleting = false;
        expectedTotalBytes = 0;
        receivedBytes = 0;

        stopPlaybackWorkerLocked();
        chunkQueue.clear();

        audioSink.stop();
        audioSink.release();

        if (listener != null && cancelledId != null) {
            listener.onPlaybackCancelled(cancelledId, reason);
        }
        logI(TAG, "Voice session cancelled: id=" + cancelledId + ", reason=" + reason);
    }

    private void stopPlaybackWorkerLocked() {
        isWorkerRunning.set(false);
        if (playbackThread != null) {
            playbackThread.interrupt();
            try {
                playbackThread.join(250);
            } catch (InterruptedException ignored) {}
            playbackThread = null;
        }
    }

    public void onDisconnected() {
        synchronized (sessionLock) {
            if (activeMessageId != null) {
                cancelCurrentSessionLocked("websocket_disconnected");
            }
        }
    }

    public void release() {
        synchronized (sessionLock) {
            cancelCurrentSessionLocked("player_released");
        }
    }

    public String getActiveMessageId() {
        synchronized (sessionLock) {
            return activeMessageId;
        }
    }

    public int getReceivedBytes() {
        synchronized (sessionLock) {
            return receivedBytes;
        }
    }
}
