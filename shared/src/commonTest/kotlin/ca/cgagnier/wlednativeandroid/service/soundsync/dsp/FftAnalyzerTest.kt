package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FftAnalyzerTest {

    private val sampleRate = 44100

    @Test
    fun analyze_returns16Channels() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val samples = FloatArray(512) { 0.1f }
        val result = analyzer.analyze(samples)
        assertEquals(16, result.fftChannels.size)
    }

    @Test
    fun analyze_channelValuesAreInByteRange() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        // Generate a loud signal
        val samples = generateSineWave(frequency = 1000f, sampleRate = sampleRate, count = 512, amplitude = 0.9f)
        val result = analyzer.analyze(samples)
        for (i in result.fftChannels.indices) {
            val value = result.fftChannels[i].toInt() and 0xFF
            assertTrue(value in 0..255, "Channel $i value $value should be in 0-255 range")
        }
    }

    @Test
    fun analyze_silenceProducesZeroChannels() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val samples = FloatArray(512) { 0f }
        val result = analyzer.analyze(samples)
        for (i in result.fftChannels.indices) {
            assertEquals(0, result.fftChannels[i].toInt(), "Channel $i should be zero for silence")
        }
        assertEquals(0f, result.magnitude, 0.001f)
        assertEquals(0f, result.majorPeak, 0.001f)
    }

    @Test
    fun analyze_lowFrequencySineWave_peaksInLowChannels() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        // 100 Hz sine wave — should peak in the first few channels
        val samples = generateSineWave(frequency = 100f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        val maxChannel = result.fftChannels.withIndex().maxByOrNull { it.value.toInt() and 0xFF }?.index ?: -1
        assertTrue(maxChannel in 0..3, "100 Hz should peak in channels 0-3, got channel $maxChannel")
    }

    @Test
    fun analyze_highFrequencySineWave_peaksInHighChannels() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        // 5000 Hz sine wave — should peak in higher channels
        val samples = generateSineWave(frequency = 5000f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        val maxChannel = result.fftChannels.withIndex().maxByOrNull { it.value.toInt() and 0xFF }?.index ?: -1
        assertTrue(maxChannel >= 10, "5000 Hz should peak in channels 10+, got channel $maxChannel")
    }

    @Test
    fun analyze_majorPeakReportsCorrectFrequency() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        // 1000 Hz sine wave
        val samples = generateSineWave(frequency = 1000f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        // Major peak should be near 1000 Hz (within one bin width: sampleRate/fftSize ≈ 86 Hz)
        val binWidth = sampleRate.toFloat() / 512
        assertTrue(
            kotlin.math.abs(result.majorPeak - 1000f) < binWidth * 2,
            "Major peak should be near 1000 Hz, got ${result.majorPeak} Hz",
        )
    }

    @Test
    fun analyze_magnitudeIsPositiveForNonSilentSignal() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val samples = generateSineWave(frequency = 440f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        assertTrue(result.magnitude > 0f, "Magnitude should be positive for non-silent signal")
    }

    @Test
    fun analyze_inputSmallerThanFftSize_padsWithZeros() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val samples = FloatArray(256) { 0.5f }
        val result = analyzer.analyze(samples)
        assertEquals(16, result.fftChannels.size)
    }

    @Test
    fun analyze_inputLargerThanFftSize_usesLastFftSizeSamples() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        // First half is silence, second half is a sine wave
        val samples = FloatArray(1024) { i ->
            if (i >= 512) sin(2.0 * PI * 1000.0 * (i - 512) / sampleRate).toFloat() * 0.5f else 0f
        }
        val result = analyzer.analyze(samples)
        // Should detect the 1000 Hz signal from the last 512 samples
        assertTrue(result.magnitude > 0f, "Should detect signal in the last 512 samples")
    }

    @Test
    fun analyze_emptyInput_returnsSilentResult() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val result = analyzer.analyze(FloatArray(0))
        assertEquals(16, result.fftChannels.size)
        for (channel in result.fftChannels) {
            assertEquals(0, channel.toInt())
        }
        assertEquals(0f, result.magnitude, 0.001f)
    }

    @Test
    fun analyze_differentFrequenciesProduceDifferentChannelPatterns() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val lowSamples = generateSineWave(frequency = 100f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val highSamples = generateSineWave(frequency = 5000f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val lowResult = analyzer.analyze(lowSamples)
        val highResult = analyzer.analyze(highSamples)
        // The channel patterns should be different
        var differences = 0
        for (i in 0 until 16) {
            if (kotlin.math.abs(
                    (lowResult.fftChannels[i].toInt() and 0xFF) -
                        (highResult.fftChannels[i].toInt() and 0xFF),
                ) > 10
            ) {
                differences++
            }
        }
        assertTrue(differences > 0, "Different frequencies should produce different channel patterns")
    }

    private fun generateSineWave(frequency: Float, sampleRate: Int, count: Int, amplitude: Float = 0.5f): FloatArray =
        FloatArray(count) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat() * amplitude
        }
}
