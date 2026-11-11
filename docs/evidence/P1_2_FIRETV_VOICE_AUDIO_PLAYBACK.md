# Fire TV In-App Voice Audio Playback (P1-2 Evidence)

## 1. Feature Purpose
This capability enables the sovereign Fire TV launcher (`com.aether.launcher`) to receive synthesized voice audio directly from the core server over the established authenticated WebSocket connection and play it through the local TV audio pipeline (`AudioTrack` in `MODE_STREAM` via `STREAM_MUSIC` / HDMI sink).

This eliminates reliance on third-party media players or external Cast receivers, providing low-latency, sovereign speech delivery directly inside the TV surface.

---

## 2. Protocol Contract
Voice streaming utilizes a framed hybrid control/binary protocol multiplexed over the existing authenticated WebSocket connection:

### Control Envelopes (JSON, Opcode 0x1)
1. **`voice_audio_start`**: Initiates a voice session.
   - `message_id`: Unique correlation identifier.
   - `encoding`: Audio encoding (strictly `pcm_s16le`).
   - `sample_rate`: Sampling rate (e.g., `22050`).
   - `channels`: Channel count (`1` for mono).
   - `total_bytes`: Declared total payload size in bytes.
   - `expires_at`: Epoch timestamp (seconds) after which message is discarded.
2. **`voice_audio_complete`**: Signals clean completion of binary audio transmission.
3. **`voice_audio_cancel`**: Aborts an in-flight utterance.
4. **`voice_audio_error`**: Reports playback or streaming errors.

### Audio Frames (Binary, Opcode 0x2)
- Raw PCM 16-bit little-endian mono audio chunks (`pcm_s16le`).
- Streamed sequentially in bounded chunks following a valid `voice_audio_start`.

---

## 3. Security Boundary & Verification
- **Authenticated Connection:** Reuses the existing TLS connection with SHA-256 certificate pin enforcement (`AeonixWebSocketClient`). Plaintext fallback is prohibited.
- **Zero URL Credentials:** No tokens, keys, or private parameters are exposed in URL query strings.
- **Permission Footprint:** No additional Android permissions added. No microphone recording or broad storage access requested for playback.
- **Payload Sanitization & Resource Bounding:**
  - Hard ceiling of 5 MiB (`5,242,880` bytes) per audio message.
  - Bounded queue capacity of 100 chunks to prevent memory exhaustion.
  - Sample rate bounded between 8,000 Hz and 48,000 Hz.
  - Immediate rejection of expired, malformed, duplicate, or oversized start envelopes.
  - Unsolicited binary frames received without an active session are dropped.

---

## 4. Playback Lifecycle & Resource Management
- **Hardware Routing:** Audio is delivered via `android.media.AudioTrack` configured with `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_MUSIC`, routing directly to `STREAM_MUSIC` (HDMI output).
- **Concurrency:** Audio decoding and buffering run on a dedicated worker thread decoupled from the UI and WebSocket network threads.
- **Deterministic Cleanup:** Hardware audio resources (`AudioTrack`) and worker threads are stopped and released immediately upon:
  - Utterance completion.
  - Session cancellation.
  - Stream replacement by a newer utterance.
  - WebSocket disconnection.
  - Activity destruction (`MainActivity.onDestroy`).

---

## 5. Test Evidence

### Automated Unit Tests
- **Suite Command:** `./gradlew testDebugUnitTest`
- **Total Test Suites:** 44
- **Total Tests Executed:** 314
- **Failures:** 0
- **Errors:** 0
- **Skipped:** 0
- **Dedicated Voice Tests:**
  - `VoiceAudioPlayerTest` (12 tests): verified start envelope parsing, duplicate detection, expiry rejection, oversized payload rejection, unsolicited binary drop, chunk reassembly, playback completion, replacement preemption, disconnect cleanup, and cancellation.
  - `AeonixWebSocketClientTest` (7 tests): verified TLS certificate pinning, mismatched pin rejection, and empty cert rejection.

### Acoustic & Transport Verification on Hardware
- **Transport Status:** PASS — authenticated, certificate-pinned WebSocket delivery; `AudioTrack` initialized; playback completed; human reported audible speech.
- **Evaluated Utterances:**
  - **Test A** (*"Hello, Aadi."*): PASS — Human reported: `“It was good.”`
  - **Test B** (*"The phone and television are connected."*): PASS — Human reported: `“The second audio was good.”`
  - **Test C** (*"Please pause, then continue."*): PASS — Human reported: `“It was good.”`
- **Pronunciation Verdict:** `“Aadi pronunciation accepted by human listener.”`

---

## 6. Explicit Limitations
1. **Full End-to-End Voice Loop Unproven:** Tests A, B, and C validated synthetic voice generation, network framing, and in-app Fire TV playback. The complete interactive loop (microphone capture -> speech-to-text -> LLM reasoning -> TTS -> TV playback) has not yet been executed as a unified end-to-end chain.
2. **Production Release Not Ready:** This capability is preserved for evaluation and integration testing; production release readiness is not yet finalized.
3. **No Claim of Bit-for-Bit PCM Delivery:** No assertion is made regarding bit-for-bit raw PCM delivery or the absence of Android audio framework resampling / mixer processing in the HDMI sink path.
4. **Phonetic Analysis Scope:** The evaluation records human acceptance of the Indian English candidate voice and pronunciation of "Aadi"; no acoustic formant analysis or specific phonetic feature transitions are claimed.
