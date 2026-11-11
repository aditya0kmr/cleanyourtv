package com.gathin.tvhome.voice;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protocol specification and parser for in-app sovereign voice streaming.
 * Handles validation of control messages over the existing authenticated WebSocket pipe.
 * Pure-Java zero-dependency parser ensures deterministic behavior across both Android
 * device runtime and host JVM unit test runners.
 */
public final class VoiceAudioProtocol {

    public static final String TYPE_START = "voice_audio_start";
    public static final String TYPE_COMPLETE = "voice_audio_complete";
    public static final String TYPE_CANCEL = "voice_audio_cancel";
    public static final String TYPE_ERROR = "voice_audio_error";

    public static final String ENCODING_PCM_S16LE = "pcm_s16le";

    // Hard ceiling: 5 MiB maximum audio payload (~113s of 22050Hz 16-bit mono)
    public static final int MAX_ALLOWED_BYTES = 5 * 1024 * 1024;
    public static final int MIN_SAMPLE_RATE = 8000;
    public static final int MAX_SAMPLE_RATE = 48000;

    public static class StartHeader {
        public final String messageId;
        public final String encoding;
        public final int sampleRate;
        public final int channels;
        public final int totalBytes;
        public final long expiresAt;

        public StartHeader(String messageId, String encoding, int sampleRate, int channels, int totalBytes, long expiresAt) {
            this.messageId = messageId;
            this.encoding = encoding;
            this.sampleRate = sampleRate;
            this.channels = channels;
            this.totalBytes = totalBytes;
            this.expiresAt = expiresAt;
        }
    }

    public static class ControlAction {
        public final String type;
        public final String messageId;
        public final String reason;

        public ControlAction(String type, String messageId, String reason) {
            this.type = type;
            this.messageId = messageId;
            this.reason = reason;
        }
    }

    private static String optString(String json, String key, String defaultVal) {
        if (json == null) return defaultVal;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        return defaultVal;
    }

    private static long optLong(String json, String key, long defaultVal) {
        if (json == null) return defaultVal;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)");
        Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Long.parseLong(m.group(1));
            } catch (Exception ignored) {}
        }
        return defaultVal;
    }

    private static int optInt(String json, String key, int defaultVal) {
        return (int) optLong(json, key, defaultVal);
    }

    private static boolean isValidJsonObject(String str) {
        if (str == null) return false;
        String trimmed = str.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    /**
     * Parses and validates a voice_audio_start JSON payload.
     * Returns StartHeader if valid, or null if malformed, expired, unsupported, or oversized.
     */
    public static StartHeader parseStartMessage(String jsonStr, long currentTimeSeconds) {
        if (!isValidJsonObject(jsonStr)) return null;

        String type = optString(jsonStr, "type", "");
        if (!TYPE_START.equals(type)) return null;

        String messageId = optString(jsonStr, "message_id", "").trim();
        if (messageId.isEmpty()) return null;

        String encoding = optString(jsonStr, "encoding", "").trim();
        if (!ENCODING_PCM_S16LE.equalsIgnoreCase(encoding)) return null;

        int sampleRate = optInt(jsonStr, "sample_rate", -1);
        if (sampleRate < MIN_SAMPLE_RATE || sampleRate > MAX_SAMPLE_RATE) return null;

        int channels = optInt(jsonStr, "channels", -1);
        if (channels != 1 && channels != 2) return null;

        int totalBytes = optInt(jsonStr, "total_bytes", -1);
        if (totalBytes <= 0 || totalBytes > MAX_ALLOWED_BYTES) return null;

        long expiresAt = optLong(jsonStr, "expires_at", -1);
        if (expiresAt > 0 && currentTimeSeconds >= expiresAt) {
            // Expired control message
            return null;
        }

        return new StartHeader(messageId, encoding, sampleRate, channels, totalBytes, expiresAt);
    }

    /**
     * Parses control actions: voice_audio_complete, voice_audio_cancel, voice_audio_error.
     * Returns ControlAction if valid, or null if unknown or malformed.
     */
    public static ControlAction parseControlAction(String jsonStr) {
        if (!isValidJsonObject(jsonStr)) return null;

        String type = optString(jsonStr, "type", "");
        String messageId = optString(jsonStr, "message_id", "").trim();
        if (messageId.isEmpty()) return null;

        if (TYPE_COMPLETE.equals(type)) {
            return new ControlAction(type, messageId, null);
        } else if (TYPE_CANCEL.equals(type)) {
            String reason = optString(jsonStr, "reason", "cancelled_by_server");
            return new ControlAction(type, messageId, reason);
        } else if (TYPE_ERROR.equals(type)) {
            String error = optString(jsonStr, "error", "unspecified_server_error");
            return new ControlAction(type, messageId, error);
        }
        return null;
    }
}
