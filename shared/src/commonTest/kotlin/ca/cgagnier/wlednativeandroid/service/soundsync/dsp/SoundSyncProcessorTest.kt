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
    fun process_sampleSmthTracksPeakEnvelopeAndDecaysSmoothly() {
        val processor = SoundSyncProcessor(sampleRate = 44100, manualGain = 1.0f)
        val windowSize = SoundSyncProcessor.FFT_WINDOW_SIZE
        val loudWave = generateSineWave(frequency = 440f, sampleRate = 44100, count = windowSize, amplitude = 0.8f)
        val silence = FloatArray(windowSize) { 0f }

        val activePacket = assertNotNull(processor.process(loudWave, nowMs = 0L))
        val decayingPacket = assertNotNull(processor.process(silence, nowMs = 20L))

        assertTrue(
            activePacket.sampleSmth > 180f,
            "sampleSmth should reach WLED's upper range on loud signal, got ${activePacket.sampleSmth}",
        )
        assertTrue(
            decayingPacket.sampleSmth > 0f && decayingPacket.sampleSmth < activePacket.sampleSmth,
            "sampleSmth should decay smoothly on silence, got ${decayingPacket.sampleSmth}",
        )
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

    private fun generateSineWave(frequency: Float, sampleRate: Int, count: Int, amplitude: Float = 0.5f): FloatArray =
        FloatArray(count) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat() * amplitude
        }
}
