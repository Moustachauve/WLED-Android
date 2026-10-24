package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LowCutFilterTest {

    private val sampleRate = 44100

    @Test
    fun process_removesDcOffset() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        // Feed a constant DC signal; after warmup, output should approach zero
        val dcSignal = FloatArray(2048) { 0.5f }
        val output = filter.process(dcSignal)
        // After processing 2048 samples, the DC component should be heavily attenuated
        val tailAverage = output.takeLast(256).average()
        assertTrue(abs(tailAverage) < 0.01, "DC offset should be removed, got $tailAverage")
    }

    @Test
    fun process_attenuatesLowFrequencies() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        // 20 Hz sine wave (well below cutoff)
        val samples = generateSineWave(frequency = 20f, sampleRate = sampleRate, count = 4096)
        val output = filter.process(samples)
        val inputRms = rms(samples.takeLast(2048))
        val outputRms = rms(output.takeLast(2048))
        // Output should be significantly attenuated compared to input
        assertTrue(
            outputRms < inputRms * 0.3,
            "Low frequency should be attenuated: input RMS=$inputRms, output RMS=$outputRms",
        )
    }

    @Test
    fun process_passesHighFrequencies() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        // 1000 Hz sine wave (well above cutoff)
        val samples = generateSineWave(frequency = 1000f, sampleRate = sampleRate, count = 4096)
        val output = filter.process(samples)
        val inputRms = rms(samples.takeLast(2048))
        val outputRms = rms(output.takeLast(2048))
        // Output should be close to input (passband)
        assertTrue(
            outputRms > inputRms * 0.7,
            "High frequency should pass: input RMS=$inputRms, output RMS=$outputRms",
        )
    }

    @Test
    fun process_outputHasSameLengthAsInput() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        val samples = FloatArray(512) { 0.1f }
        val output = filter.process(samples)
        assertEquals(samples.size, output.size)
    }

    @Test
    fun process_isStatefulAcrossCalls() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        // First call with DC
        filter.process(FloatArray(1024) { 0.5f })
        // Second call with DC — output should be near zero (filter has settled)
        val output = filter.process(FloatArray(1024) { 0.5f })
        val tailAverage = output.takeLast(256).average()
        assertTrue(abs(tailAverage) < 0.01, "Filter should be stateful, got $tailAverage")
    }

    @Test
    fun process_emptyInput_returnsEmptyOutput() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        val output = filter.process(FloatArray(0))
        assertEquals(0, output.size)
    }

    @Test
    fun reset_clearsFilterState() {
        val filter = LowCutFilter(cutoffHz = 55f, sampleRate = sampleRate)
        filter.process(FloatArray(1024) { 0.5f })
        filter.reset()
        // After reset, processing DC should start from scratch (not yet settled)
        val output = filter.process(FloatArray(256) { 0.5f })
        val tailAverage = output.takeLast(64).average()
        // Should NOT be near zero yet since we only processed 256 samples after reset
        assertTrue(abs(tailAverage) > 0.05, "After reset, filter should need warmup again")
    }

    private fun generateSineWave(frequency: Float, sampleRate: Int, count: Int): FloatArray = FloatArray(count) { i ->
        sin(2.0 * kotlin.math.PI * frequency * i / sampleRate).toFloat() * 0.5f
    }

    private fun rms(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.sumOf { (it * it).toDouble() }
        return kotlin.math.sqrt(sumSquares / samples.size).toFloat()
    }
}
