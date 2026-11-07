package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SoundSyncProcessorTest {

    @Test
    fun process_emptyInput_returnsNull() {
        val processor = SoundSyncProcessor()
        assertNull(processor.process(FloatArray(0), nowMs = 100L))
    }

    @Test
    fun process_throttlesFramesWithinPacketInterval() {
        val processor = SoundSyncProcessor()
        val samples = generateSineWave(frequency = 440f, sampleRate = 44100, count = 256)

        val firstFrame = processor.process(samples, nowMs = 1000L)
        val throttledFrame = processor.process(samples, nowMs = 1000L + UdpSoundSyncPacket.PACKET_INTERVAL_MS - 1)
        val nextFrame = processor.process(samples, nowMs = 1000L + UdpSoundSyncPacket.PACKET_INTERVAL_MS)

        assertNotNull(firstFrame)
        assertNull(throttledFrame)
        assertNotNull(nextFrame)
    }

    @Test
    fun process_accumulatesFilteredAndGainedSamplesDuringThrottledIntervals() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 4.0f)
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val silence = FloatArray(windowSize) { 0f }
        // Emit initial silent frame at t = 0
        assertNotNull(processor.process(silence, nowMs = 0L))

        // Feed a full-window 1000 Hz sine wave during a throttled interval (t = 10ms)
        val sineWave = generateSineWave(frequency = 1000f, sampleRate = 44100, count = windowSize, amplitude = 0.2f)
        assertNull(processor.process(sineWave, nowMs = 10L))

        // At t = 20ms, feed a tiny 1-sample chunk; the FFT window and peak should retain the throttled samples
        val packet = assertNotNull(processor.process(floatArrayOf(0f), nowMs = 20L))
        val binWidth = 44100f / windowSize
        assertTrue(packet.sampleRaw > 100f, "Peak from throttled chunk should be retained in sampleRaw")
        assertTrue(packet.fftMagnitude > 0f, "FFT window should retain throttled samples")
        assertTrue(
            abs(packet.fftMajorPeak - 1000f) < binWidth * 2,
            "Expected major peak near 1000 Hz from accumulated throttled samples, got ${packet.fftMajorPeak}",
        )
    }

    @Test
    fun process_sampleSmthReachesUpperRangeOnLoudSignal() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f)
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val loudWave = generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize, amplitude = 0.8f)

        assertNotNull(processor.process(loudWave, nowMs = 0L))
        val secondPacket = assertNotNull(processor.process(loudWave, nowMs = 20L))

        assertTrue(
            secondPacket.sampleSmth > 180f,
            "sampleSmth should reach WLED's upper range on loud signal, got ${secondPacket.sampleSmth}",
        )
    }

    @Test
    fun process_sampleSmthDecaysSmoothlyOnSilence() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f)
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val loudWave = generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize, amplitude = 0.8f)
        val silence = FloatArray(windowSize) { 0f }

        val activePacket = assertNotNull(processor.process(loudWave, nowMs = 0L))
        val decayingPacket = assertNotNull(processor.process(silence, nowMs = 20L))

        assertTrue(
            decayingPacket.sampleSmth > 0f && decayingPacket.sampleSmth < activePacket.sampleSmth,
            "sampleSmth should decay smoothly on silence, got ${decayingPacket.sampleSmth}",
        )
    }

    @Test
    fun process_whenAgcEnabled_roomNoiseDoesNotOpenNoiseGate() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f, agcMode = AgcMode.NORMAL)
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        // Quiet passage just above the gate (0.006 > 0.005) ramps AGC; then room drops to 0.003 noise
        val quietPassage =
            generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize * 4, amplitude = 0.006f)
        val roomNoise = generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize, amplitude = 0.003f)

        assertNotNull(processor.process(quietPassage, nowMs = 0L))
        var silentPacket: UdpSoundSyncPacket? = null
        repeat(SILENT_DECAY_FRAME_COUNT) { frame ->
            silentPacket = processor.process(
                roomNoise,
                nowMs = (frame + 1) * UdpSoundSyncPacket.PACKET_INTERVAL_MS,
            )
        }
        val finalPacket = assertNotNull(silentPacket)

        assertEquals(
            0f,
            finalPacket.sampleRaw,
            0.001f,
            "Pre-gain noise gate should block room noise even at high AGC gain",
        )
        assertEquals(0f, finalPacket.sampleSmth, 0.001f, "sampleSmth should decay to 0 on room noise")
        assertEquals(0.001f, finalPacket.fftMagnitude, 0.0001f, "fftMagnitude should be muted when sampleSmth < 1")
    }

    @Test
    fun setSquelch_higherThresholdGatesModerateBackgroundNoise() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 5.0f, squelch = 50)
        // squelch = 50 -> threshold = 0.025f; 0.02f signal should be gated to 0
        val noise = generateSineWave(
            frequency = 440f,
            sampleRate = 44100,
            count = SoundSyncProcessor.FFT_WINDOW_SIZE,
            amplitude = 0.02f,
        )

        val packet = assertNotNull(processor.process(noise, nowMs = 0L))

        assertEquals(0f, packet.sampleRaw, 0.001f)
    }

    @Test
    fun process_lowLevelNoiseDoesNotTriggerBeatPeak() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f)
        val silence = FloatArray(256) { 0f }
        val veryQuietNoise = generateSineWave(frequency = 440f, sampleRate = 44100, count = 256, amplitude = 0.01f)

        // Warm up with silence
        assertNotNull(processor.process(silence, nowMs = 0L))
        // Very quiet noise (0.01 < BeatDetector.DEFAULT_MIN_ENERGY = 0.05) should not trigger a peak
        val packet = assertNotNull(processor.process(veryQuietNoise, nowMs = 25L))
        assertFalse(packet.samplePeak, "Sub-threshold noise (0.01 amplitude) should not trigger a peak")
    }

    @Test
    fun configureSampleRate_updatesFftFrequencyMapping() {
        val processor = SoundSyncProcessor(sampleRate = 44100)
        processor.configureSampleRate(48000)
        assertEquals(48000, processor.sampleRate)

        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val samples = generateSineWave(frequency = 2000f, sampleRate = 48000, count = windowSize, amplitude = 0.5f)
        val packet = assertNotNull(processor.process(samples, nowMs = 100L))
        val binWidth = 48000f / windowSize
        assertTrue(
            abs(packet.fftMajorPeak - 2000f) < binWidth * 2,
            "Expected major peak near 2000 Hz at 48 kHz sample rate, got ${packet.fftMajorPeak}",
        )
    }

    @Test
    fun process_normalizesSampleSmthRiseAcrossDifferentFrameCadences() {
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val loudWave = generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize, amplitude = 0.8f)

        val twentyMsProcessor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f)
        val fortyMsProcessor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f)

        assertNotNull(twentyMsProcessor.process(loudWave, nowMs = 0L))
        assertNotNull(fortyMsProcessor.process(loudWave, nowMs = 0L))
        assertNotNull(twentyMsProcessor.process(loudWave, nowMs = 20L))
        val twoTwentyMsRise = assertNotNull(twentyMsProcessor.process(loudWave, nowMs = 40L))
        val oneFortyMsRise = assertNotNull(fortyMsProcessor.process(loudWave, nowMs = 40L))

        assertEquals(twoTwentyMsRise.sampleSmth, oneFortyMsRise.sampleSmth, 0.01f)
    }

    @Test
    fun process_normalizesSampleSmthDecayAcrossDifferentFrameCadences() {
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val loudWave = generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize, amplitude = 0.8f)
        val silence = FloatArray(windowSize) { 0f }

        val twentyMsProcessor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f).apply {
            assertNotNull(process(loudWave, nowMs = 0L))
            // Flush the high-pass filter tail at 20 ms so subsequent silent frames have zero sampleRaw
            assertNotNull(process(silence, nowMs = 20L))
        }
        val fortyMsProcessor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f).apply {
            assertNotNull(process(loudWave, nowMs = 0L))
            assertNotNull(process(silence, nowMs = 20L))
        }

        assertNotNull(twentyMsProcessor.process(silence, nowMs = 40L))
        val twoTwentyMsDecay = assertNotNull(twentyMsProcessor.process(silence, nowMs = 60L))
        val oneFortyMsDecay = assertNotNull(fortyMsProcessor.process(silence, nowMs = 60L))

        assertEquals(twoTwentyMsDecay.sampleSmth, oneFortyMsDecay.sampleSmth, 0.01f)
    }

    @Test
    fun process_whenAgcEnabled_normalizesEffectiveGainSmoothingAcrossFrameCadences() {
        val quietWave = generateSineWave(
            frequency = 440f,
            sampleRate = 44100,
            count = SoundSyncProcessor.FFT_WINDOW_SIZE * 2,
            amplitude = 0.02f,
        )
        val twentyMsProcessor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.5f, agcMode = AgcMode.NORMAL)
        val fortyMsProcessor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.5f, agcMode = AgcMode.NORMAL)

        assertNotNull(twentyMsProcessor.process(quietWave, nowMs = 0L))
        assertNotNull(fortyMsProcessor.process(quietWave, nowMs = 0L))
        assertNotNull(twentyMsProcessor.process(quietWave, nowMs = 20L))
        assertNotNull(twentyMsProcessor.process(quietWave, nowMs = 40L))
        assertNotNull(fortyMsProcessor.process(quietWave, nowMs = 40L))

        assertEquals(twentyMsProcessor.effectiveGain, fortyMsProcessor.effectiveGain, 0.01f)
    }

    @Test
    fun setAgcMode_fromOffWithHighManualGain_startsEffectiveGainAtDefaultGain() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 8.0f, agcMode = AgcMode.OFF)
        assertEquals(8.0f, processor.effectiveGain, 0.0001f)

        processor.setAgcMode(AgcMode.NORMAL)

        assertEquals(SoundSyncProcessor.DEFAULT_GAIN, processor.effectiveGain, 0.0001f)
    }

    @Test
    fun setAgcMode_betweenActiveModes_preservesSmoothedEffectiveGain() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f, agcMode = AgcMode.VIVID)
        val quietWave = generateSineWave(
            frequency = 440f,
            sampleRate = 44100,
            count = SoundSyncProcessor.FFT_WINDOW_SIZE * 4,
            amplitude = 0.02f,
        )
        assertNotNull(processor.process(quietWave, nowMs = 0L))
        val activeGain = processor.effectiveGain
        assertTrue(activeGain > SoundSyncProcessor.DEFAULT_GAIN)

        processor.setAgcMode(AgcMode.LAZY)

        assertEquals(activeGain, processor.effectiveGain, 0.0001f)
    }

    @Test
    fun process_maintainsFiftyHertzCadenceAcrossTenMillisecondSubFrames() {
        val processor = SoundSyncProcessor(sampleRate = 48000)
        val subFrame = generateSineWave(frequency = 440f, sampleRate = 48000, count = 512, amplitude = 0.5f)

        // 50 sub-frames of 512 samples at 48 kHz (~533 ms total) should emit 27 packets (~50 Hz)
        var emittedCount = 0
        for (chunkIndex in 0 until 50) {
            val sampleTimeMs = chunkIndex * 512L * 1000L / 48000L
            if (processor.process(subFrame, nowMs = sampleTimeMs) != null) {
                emittedCount++
            }
        }

        assertEquals(27, emittedCount)
    }

    private fun generateSineWave(frequency: Float, sampleRate: Int, count: Int, amplitude: Float = 0.5f): FloatArray =
        FloatArray(count) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat() * amplitude
        }

    companion object {
        private const val SILENT_DECAY_FRAME_COUNT = 25
    }
}
