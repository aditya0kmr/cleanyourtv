package com.gathin.tvhome.voice;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VoiceAudioPlayerTest {

    private static class TestAudioSink implements AudioSink {
        int initCount = 0;
        int lastSampleRate = 0;
        int lastChannels = 0;
        int lastEncoding = 0;

        int playCount = 0;
        int stopCount = 0;
        int releaseCount = 0;

        final ByteArrayOutputStream writtenBytes = new ByteArrayOutputStream();
        volatile boolean playing = false;

        @Override
        public synchronized void init(int sampleRate, int channels, int encoding, int minBufferSize) {
            initCount++;
            lastSampleRate = sampleRate;
            lastChannels = channels;
            lastEncoding = encoding;
            writtenBytes.reset();
            playing = false;
        }

        @Override
        public synchronized int write(byte[] data, int offset, int size) {
            writtenBytes.write(data, offset, size);
            return size;
        }

        @Override
        public synchronized void play() {
            playCount++;
            playing = true;
        }

        @Override
        public synchronized void stop() {
            stopCount++;
            playing = false;
        }

        @Override
        public synchronized void release() {
            releaseCount++;
            playing = false;
        }

        @Override
        public synchronized boolean isPlaying() {
            return playing;
        }
    }

    private TestAudioSink testSink;
    private VoiceAudioPlayer player;

    @Before
    public void setUp() {
        testSink = new TestAudioSink();
        player = new VoiceAudioPlayer(testSink);
    }

    @Test
    public void testValidStartMessageParsing() {
        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        String json = "{\n" +
                "  \"type\": \"voice_audio_start\",\n" +
                "  \"message_id\": \"msg-101\",\n" +
                "  \"encoding\": \"pcm_s16le\",\n" +
                "  \"sample_rate\": 22050,\n" +
                "  \"channels\": 1,\n" +
                "  \"total_bytes\": 57900,\n" +
                "  \"expires_at\": " + futureSec + "\n" +
                "}";

        VoiceAudioProtocol.StartHeader header = VoiceAudioProtocol.parseStartMessage(json, System.currentTimeMillis() / 1000);
        assertNotNull(header);
        assertEquals("msg-101", header.messageId);
        assertEquals("pcm_s16le", header.encoding);
        assertEquals(22050, header.sampleRate);
        assertEquals(1, header.channels);
        assertEquals(57900, header.totalBytes);
        assertEquals(futureSec, header.expiresAt);
    }

    @Test
    public void testMalformedStartMessageRejection() {
        long futureSec = (System.currentTimeMillis() / 1000) + 60;

        // Missing message_id
        String noId = "{\"type\":\"voice_audio_start\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":100,\"expires_at\":" + futureSec + "}";
        assertNull(VoiceAudioProtocol.parseStartMessage(noId, System.currentTimeMillis() / 1000));

        // Invalid encoding
        String badEnc = "{\"type\":\"voice_audio_start\",\"message_id\":\"m1\",\"encoding\":\"mp3\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":100,\"expires_at\":" + futureSec + "}";
        assertNull(VoiceAudioProtocol.parseStartMessage(badEnc, System.currentTimeMillis() / 1000));

        // Negative sample rate
        String badRate = "{\"type\":\"voice_audio_start\",\"message_id\":\"m1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":-1,\"channels\":1,\"total_bytes\":100,\"expires_at\":" + futureSec + "}";
        assertNull(VoiceAudioProtocol.parseStartMessage(badRate, System.currentTimeMillis() / 1000));

        // Invalid channels
        String badChannels = "{\"type\":\"voice_audio_start\",\"message_id\":\"m1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":5,\"total_bytes\":100,\"expires_at\":" + futureSec + "}";
        assertNull(VoiceAudioProtocol.parseStartMessage(badChannels, System.currentTimeMillis() / 1000));

        // Null / garbage
        assertNull(VoiceAudioProtocol.parseStartMessage("", System.currentTimeMillis() / 1000));
        assertNull(VoiceAudioProtocol.parseStartMessage("invalid json", System.currentTimeMillis() / 1000));
    }

    @Test
    public void testOversizedTotalBytesRejected() {
        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        int tooLarge = VoiceAudioProtocol.MAX_ALLOWED_BYTES + 1024;
        String json = "{\"type\":\"voice_audio_start\",\"message_id\":\"m1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":" + tooLarge + ",\"expires_at\":" + futureSec + "}";
        assertNull(VoiceAudioProtocol.parseStartMessage(json, System.currentTimeMillis() / 1000));
    }

    @Test
    public void testExpiredStartMessageRejected() {
        long pastSec = (System.currentTimeMillis() / 1000) - 10;
        String json = "{\"type\":\"voice_audio_start\",\"message_id\":\"m1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":100,\"expires_at\":" + pastSec + "}";
        assertNull(VoiceAudioProtocol.parseStartMessage(json, System.currentTimeMillis() / 1000));
    }

    @Test
    public void testControlActionParsing() {
        VoiceAudioProtocol.ControlAction comp = VoiceAudioProtocol.parseControlAction("{\"type\":\"voice_audio_complete\",\"message_id\":\"m1\"}");
        assertNotNull(comp);
        assertEquals(VoiceAudioProtocol.TYPE_COMPLETE, comp.type);
        assertEquals("m1", comp.messageId);

        VoiceAudioProtocol.ControlAction canc = VoiceAudioProtocol.parseControlAction("{\"type\":\"voice_audio_cancel\",\"message_id\":\"m2\",\"reason\":\"user_interrupt\"}");
        assertNotNull(canc);
        assertEquals(VoiceAudioProtocol.TYPE_CANCEL, canc.type);
        assertEquals("m2", canc.messageId);
        assertEquals("user_interrupt", canc.reason);

        VoiceAudioProtocol.ControlAction err = VoiceAudioProtocol.parseControlAction("{\"type\":\"voice_audio_error\",\"message_id\":\"m3\",\"error\":\"device_overheat\"}");
        assertNotNull(err);
        assertEquals(VoiceAudioProtocol.TYPE_ERROR, err.type);
        assertEquals("m3", err.messageId);
        assertEquals("device_overheat", err.reason);
    }

    @Test
    public void testBinaryFrameReassemblyAndPlayback() throws Exception {
        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicBoolean started = new AtomicBoolean(false);
        AtomicBoolean completed = new AtomicBoolean(false);

        player.setListener(new VoiceAudioPlayer.PlaybackListener() {
            @Override
            public void onPlaybackStarted(String messageId) {
                started.set(true);
            }

            @Override
            public void onPlaybackCompleted(String messageId) {
                completed.set(true);
                completeLatch.countDown();
            }

            @Override
            public void onPlaybackCancelled(String messageId, String reason) {}

            @Override
            public void onPlaybackError(String messageId, String error) {}
        });

        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        String startJson = "{\"type\":\"voice_audio_start\",\"message_id\":\"msg-42\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":6,\"expires_at\":" + futureSec + "}";

        player.handleControlMessage(startJson);
        assertEquals("msg-42", player.getActiveMessageId());
        assertTrue(started.get());
        assertEquals(1, testSink.initCount);
        assertEquals(22050, testSink.lastSampleRate);

        // Stream two 3-byte chunks
        byte[] chunk1 = new byte[]{1, 2, 3};
        byte[] chunk2 = new byte[]{4, 5, 6};

        player.handleBinaryFrame(chunk1);
        player.handleBinaryFrame(chunk2);

        // Complete message
        player.handleControlMessage("{\"type\":\"voice_audio_complete\",\"message_id\":\"msg-42\"}");

        assertTrue("Timed out waiting for playback completion", completeLatch.await(2, TimeUnit.SECONDS));
        assertTrue(completed.get());

        // Verify audio sink received exact 6 bytes in correct order
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6}, testSink.writtenBytes.toByteArray());
        assertTrue(testSink.playCount >= 1);
        assertTrue(testSink.releaseCount >= 1);
        assertNull(player.getActiveMessageId());
    }

    @Test
    public void testExcessBinaryDataClamped() throws Exception {
        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        String startJson = "{\"type\":\"voice_audio_start\",\"message_id\":\"clamp-1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":4,\"expires_at\":" + futureSec + "}";
        player.handleControlMessage(startJson);

        // Feed 10 bytes when total_bytes was 4
        player.handleBinaryFrame(new byte[]{10, 20, 30, 40, 50, 60, 70, 80, 90, 100});
        assertEquals(4, player.getReceivedBytes());
    }

    @Test
    public void testUnsolicitedBinaryFramesDroppedWithoutStart() {
        player.handleBinaryFrame(new byte[]{1, 2, 3});
        assertEquals(0, player.getReceivedBytes());
        assertEquals(0, testSink.writtenBytes.size());
    }

    @Test
    public void testDuplicateStartMessageIgnored() {
        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        String startJson = "{\"type\":\"voice_audio_start\",\"message_id\":\"dup-1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":100,\"expires_at\":" + futureSec + "}";

        player.handleControlMessage(startJson);
        assertEquals(1, testSink.initCount);

        // Send exact duplicate
        player.handleControlMessage(startJson);
        assertEquals(1, testSink.initCount); // Must not re-initialize
    }

    @Test
    public void testCancellationStopsAndReleasesSink() throws Exception {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        player.setListener(new VoiceAudioPlayer.PlaybackListener() {
            @Override
            public void onPlaybackStarted(String messageId) {}
            @Override
            public void onPlaybackCompleted(String messageId) {}
            @Override
            public void onPlaybackCancelled(String messageId, String reason) {
                cancelLatch.countDown();
            }
            @Override
            public void onPlaybackError(String messageId, String error) {}
        });

        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        String startJson = "{\"type\":\"voice_audio_start\",\"message_id\":\"canc-1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":5000,\"expires_at\":" + futureSec + "}";
        player.handleControlMessage(startJson);

        player.handleBinaryFrame(new byte[]{1, 2, 3, 4});

        // Cancel
        player.handleControlMessage("{\"type\":\"voice_audio_cancel\",\"message_id\":\"canc-1\",\"reason\":\"user_pressed_back\"}");

        assertTrue("Timed out waiting for cancellation", cancelLatch.await(1, TimeUnit.SECONDS));
        assertNull(player.getActiveMessageId());
        assertTrue(testSink.stopCount >= 1);
        assertTrue(testSink.releaseCount >= 1);
    }

    @Test
    public void testReplacementCancelsPreviousUtterance() throws Exception {
        CountDownLatch cancelLatch = new CountDownLatch(1);
        player.setListener(new VoiceAudioPlayer.PlaybackListener() {
            @Override
            public void onPlaybackStarted(String messageId) {}
            @Override
            public void onPlaybackCompleted(String messageId) {}
            @Override
            public void onPlaybackCancelled(String messageId, String reason) {
                assertEquals("replaced_by_new_utterance", reason);
                cancelLatch.countDown();
            }
            @Override
            public void onPlaybackError(String messageId, String error) {}
        });

        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        player.handleControlMessage("{\"type\":\"voice_audio_start\",\"message_id\":\"utt-1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":1000,\"expires_at\":" + futureSec + "}");
        assertEquals("utt-1", player.getActiveMessageId());

        // Replace with new utterance before first is completed
        player.handleControlMessage("{\"type\":\"voice_audio_start\",\"message_id\":\"utt-2\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":500,\"expires_at\":" + futureSec + "}");

        assertTrue("Timed out waiting for replacement cancel", cancelLatch.await(1, TimeUnit.SECONDS));
        assertEquals("utt-2", player.getActiveMessageId());
        assertEquals(2, testSink.initCount);
    }

    @Test
    public void testDisconnectCancelsAndReleases() {
        long futureSec = (System.currentTimeMillis() / 1000) + 60;
        player.handleControlMessage("{\"type\":\"voice_audio_start\",\"message_id\":\"disc-1\",\"encoding\":\"pcm_s16le\",\"sample_rate\":22050,\"channels\":1,\"total_bytes\":1000,\"expires_at\":" + futureSec + "}");
        assertEquals("disc-1", player.getActiveMessageId());

        player.onDisconnected();
        assertNull(player.getActiveMessageId());
        assertTrue(testSink.releaseCount >= 1);
    }
}
