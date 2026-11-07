package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt
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
    fun process_withHighManualGain_allowsUpToMaxManualGain() {
        val gain = GainControl(manualGain = 25.0f, agcEnabled = false)
        val input = floatArrayOf(0.02f)
        val output = gain.process(input)
        assertEquals(0.5f, output[0], 0.0001f)
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
        val gain = GainControl(manualGain = 10.0f, agcEnabled = false)
        val input = floatArrayOf(0.5f, -0.5f)
        val output = gain.process(input)
        assertTrue(abs(output[0]) <= 1.0f, "Output should be clipped to 1.0")
        assertTrue(abs(output[1]) <= 1.0f, "Output should be clipped to -1.0")
    }

    @Test
    fun process_agcNormalizesLowSignal() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = true, agcMode = AgcMode.NORMAL)
        val input = sineWave(amplitude = 0.02f, count = 44100)
        val output = gain.process(input)
        val tailRms = rms(output.takeLast(4096))
        assertTrue(
            tailRms > 0.15f,
            "AGC should boost quiet signal: RMS=$tailRms",
        )
    }

    @Test
    fun process_agcAttenuatesLoudSignal() {
        val gain = GainControl(
            manualGain = 1.0f,
            agcEnabled = true,
            agcMode = AgcMode.NORMAL,
            manualGainMultiplier = 3.0f,
        )
        val input = sineWave(amplitude = 0.9f, count = 44100)
        val output = gain.process(input)
        val tailRms = rms(output.takeLast(4096))
        assertTrue(
            tailRms < 0.6f,
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
    fun setAgcEnabled_enablesAgcBoost() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = false, agcMode = AgcMode.VIVID)
        gain.setAgcEnabled(true)
        val input = FloatArray(22050) { 0.02f }
        val output = gain.process(input)
        val avg = output.takeLast(2048).average()
        assertTrue(avg > 0.1f, "AGC should boost signal after being enabled, got $avg")
    }

    @Test
    fun reset_clearsAgcState() {
        val gain = GainControl(manualGain = 1.0f, agcEnabled = true, agcMode = AgcMode.VIVID)
        gain.process(FloatArray(22050) { 0.02f })
        gain.reset()
        assertEquals(1.0f, gain.effectiveGain, 0.0001f)
    }

    @Test
    fun process_whenAgcEnabled_ignoresDisabledManualGainSetting() {
        val input = sineWave(amplitude = 0.05f, count = 22050)
        val zeroManualGainAgc = GainControl(manualGain = 0.0f, agcEnabled = true, agcMode = AgcMode.NORMAL)
        val highManualGainAgc = GainControl(manualGain = 10.0f, agcEnabled = true, agcMode = AgcMode.NORMAL)

        val zeroGainRms = rms(zeroManualGainAgc.process(input).takeLast(1024))
        val highGainRms = rms(highManualGainAgc.process(input).takeLast(1024))

        assertTrue(zeroGainRms > 0.1f, "AGC should normalize signal even if manualGain was 0.0f, got $zeroGainRms")
        assertEquals(zeroGainRms, highGainRms, 0.0001f)
    }

    @Test
    fun effectiveGain_whenAgcEnabled_dividesAgcGainByManualGainMultiplier() {
        val gain = GainControl(
            manualGain = 1.5f,
            agcEnabled = true,
            agcMode = AgcMode.VIVID,
            manualGainMultiplier = 3.0f,
        )
        // 0.1f peak -> desiredGain = 0.65 / 0.1 = 6.5 -> mappedAgcGain = 6.5 / 3.0 = 2.167
        val input = sineWave(amplitude = 0.1f, count = 44100 * 6)

        gain.process(input)

        assertEquals(AgcMode.VIVID.targetLevel / 0.1f / 3.0f, gain.effectiveGain, 0.1f)
    }

    @Test
    fun process_whenAgcEnabled_doesNotMutateManualGain() {
        val gain = GainControl(
            manualGain = 1.5f,
            agcEnabled = true,
            agcMode = AgcMode.NORMAL,
            manualGainMultiplier = 3.0f,
        )
        gain.process(sineWave(amplitude = 0.1f, count = 4096))

        assertEquals(1.5f, gain.manualGain, 0.0001f)
    }

    @Test
    fun process_vividAdjustsFasterThanNormalAndNormalFasterThanLazy() {
        // Amplitude 0.30f starts inside the normal zone (0.30 * 1.0 = 0.30 in 0.14..0.90)
        // and moves upward toward targetLevel (0.55..0.65 -> gain ~1.83..2.16)
        val input = sineWave(amplitude = 0.30f, count = 22050)
        val vivid = GainControl(agcEnabled = true, agcMode = AgcMode.VIVID)
        val normal = GainControl(agcEnabled = true, agcMode = AgcMode.NORMAL)
        val lazy = GainControl(agcEnabled = true, agcMode = AgcMode.LAZY)

        vivid.process(input)
        normal.process(input)
        lazy.process(input)

        assertTrue(
            vivid.effectiveGain > normal.effectiveGain,
            "Vivid (${vivid.effectiveGain}) should adjust faster than Normal (${normal.effectiveGain})",
        )
        assertTrue(
            normal.effectiveGain > lazy.effectiveGain,
            "Normal (${normal.effectiveGain}) should adjust faster than Lazy (${lazy.effectiveGain})",
        )
    }

    @Test
    fun process_belowSquelchAmplitude_holdsAgcGainSteady() {
        val gain = GainControl(
            manualGain = 1.0f,
            agcEnabled = true,
            agcMode = AgcMode.VIVID,
            squelchAmplitude = 0.01f,
            manualGainMultiplier = 3.0f,
        )
        // Signal at 0.008f is below squelchAmplitude (0.01f), so AGC must not ramp up gain
        gain.process(sineWave(amplitude = 0.008f, count = 44100))

        assertEquals(1.0f, gain.effectiveGain, 0.0001f)
    }

    @Test
    fun setAgcMode_switchingBetweenActiveModes_preservesCurrentGain() {
        val gain = GainControl(
            manualGain = 1.0f,
            agcEnabled = true,
            agcMode = AgcMode.VIVID,
            manualGainMultiplier = 3.0f,
        )
        gain.process(sineWave(amplitude = 0.02f, count = 22050))
        val boostedGain = gain.effectiveGain
        assertTrue(boostedGain > 2.0f)

        gain.setAgcMode(AgcMode.LAZY)

        assertEquals(boostedGain, gain.effectiveGain, 0.0001f)
    }

    private fun sineWave(amplitude: Float, count: Int, sampleRate: Int = 44100): FloatArray = FloatArray(count) { i ->
        amplitude * sin(2.0 * PI * 440.0 * i / sampleRate).toFloat()
    }

    private fun rms(samples: List<Float>): Float {
        if (samples.isEmpty()) return 0f
        val sumSquares = samples.sumOf { (it * it).toDouble() }
        return sqrt(sumSquares / samples.size).toFloat()
    }
}
