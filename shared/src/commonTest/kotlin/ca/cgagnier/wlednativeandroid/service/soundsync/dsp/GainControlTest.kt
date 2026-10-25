package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GainControlTest {

    @Test
    fun process_withUnityGain_returnsInputUnchanged() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = false)
        val input = floatArrayOf(0.1f, -0.2f, 0.3f, -0.1f)
        val output = gain.process(input)
        assertEquals(input.size, output.size)
        for (i in input.indices) {
            assertEquals(input[i], output[i], 0.0001f)
        }
    }

    @Test
    fun process_withGainOf2_doublesAmplitude() {
        val gain = GainControl(manualGain = 2.0f, agcEnabled = false)
        val input = floatArrayOf(0.1f, -0.2f, 0.3f)
        val output = gain.process(input)
        assertEquals(0.2f, output[0], 0.0001f)
        assertEquals(-0.4f, output[1], 0.0001f)
        assertEquals(0.6f, output[2], 0.0001f)
    }

    @Test
    fun process_withZeroGain_returnsSilence() {
        val gain = GainControl(manualGain = 0.0f, agcEnabled = false)
        val input = floatArrayOf(0.5f, -0.5f, 0.3f)
        val output = gain.process(input)
        for (sample in output) {
            assertEquals(0f, sample, 0.0001f)
        }
    }

    @Test
    fun process_clipsToMaxAmplitude() {
        val gain = GainControl(manualGain = 10.0f, agcEnabled = false, maxAmplitude = 1.0f)
        val input = floatArrayOf(0.5f, -0.5f)
        val output = gain.process(input)
        assertTrue(abs(output[0]) <= 1.0f, "Output should be clipped to 1.0")
        assertTrue(abs(output[1]) <= 1.0f, "Output should be clipped to -1.0")
    }

    @Test
    fun process_agcNormalizesLowSignal() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = true, targetLevel = 0.5f)
        // Feed a quiet signal; AGC should boost it
        val input = FloatArray(4096) { 0.01f * kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * it / 44100.0).toFloat() }
        val output = gain.process(input)
        val tailRms = rms(output.takeLast(2048))
        // AGC should bring the RMS close to the target level
        assertTrue(
            tailRms > 0.1f,
            "AGC should boost quiet signal: RMS=$tailRms, target=0.5",
        )
    }

    @Test
    fun process_agcAttenuatesLoudSignal() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = true, targetLevel = 0.3f)
        // Feed a loud signal; AGC should attenuate it
        val input = FloatArray(4096) { 0.9f * kotlin.math.sin(2.0 * kotlin.math.PI * 440.0 * it / 44100.0).toFloat() }
        val output = gain.process(input)
        val tailRms = rms(output.takeLast(2048))
        // AGC should bring the RMS down toward the target level
        assertTrue(
            tailRms < 0.7f,
            "AGC should attenuate loud signal: RMS=$tailRms",
        )
    }

    @Test
    fun process_emptyInput_returnsEmptyOutput() {
        val gain = GainControl()
        val output = gain.process(FloatArray(0))
        assertEquals(0, output.size)
    }

    @Test
    fun setManualGain_updatesGainFactor() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = false)
        gain.setManualGain(3.0f)
        val input = floatArrayOf(0.1f)
        val output = gain.process(input)
        assertEquals(0.3f, output[0], 0.0001f)
    }

    @Test
    fun setAgcEnabled_togglesAgc() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = false)
        gain.setAgcEnabled(true)
        // With AGC enabled and a quiet signal, output should be boosted
        val input = FloatArray(4096) { 0.01f }
        val output = gain.process(input)
        val avg = output.takeLast(2048).average()
        assertTrue(avg > 0.05f, "AGC should boost signal after being enabled")
    }

    @Test
    fun reset_clearsAgcState() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = true, targetLevel = 0.5f)
        // Process loud signal to build up AGC state
        gain.process(FloatArray(4096) { 0.9f })
        gain.reset()
        // After reset, AGC gain should be back to 1.0
        val input = floatArrayOf(0.1f)
        val output = gain.process(input)
        // With reset AGC, the first sample should be close to manual gain only
        assertTrue(abs(output[0] - 0.1f) < 0.2f, "After reset, gain should be near unity")
    }

    private fun rms(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.sumOf { (it * it).toDouble() }
        return kotlin.math.sqrt(sumSquares / samples.size).toFloat()
    }
}
