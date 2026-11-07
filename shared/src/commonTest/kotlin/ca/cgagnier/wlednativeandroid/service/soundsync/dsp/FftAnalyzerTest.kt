package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.abs
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
        val samples = generateSineWave(frequency = 1000f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        val binWidth = sampleRate.toFloat() / 512
        assertTrue(
            abs(result.majorPeak - 1000f) < binWidth * 2,
            "Major peak should be near 1000 Hz, got ${result.majorPeak} Hz",
        )
    }

    @Test
    fun analyze_scalesMagnitudeToWledRange() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val samples = generateSineWave(frequency = 440f, sampleRate = sampleRate, count = 512, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        assertTrue(
            result.magnitude > 5000f,
            "Magnitude should be scaled to WLED raw FFT_Magnitude range (16x channel scale), got ${result.magnitude}",
        )
    }

    @Test
    fun analyze_inputSmallerThanFftSize_padsWithZerosAndDetectsSignal() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        val samples = generateSineWave(frequency = 1000f, sampleRate = sampleRate, count = 256, amplitude = 0.5f)
        val result = analyzer.analyze(samples)
        val binWidth = sampleRate.toFloat() / 512
        assertEquals(16, result.fftChannels.size)
        assertTrue(result.magnitude > 0f, "256-sample sine wave should produce non-zero magnitude")
        assertTrue(
            abs(result.majorPeak - 1000f) < binWidth * 3,
            "256-sample sine wave should still peak near 1000 Hz, got ${result.majorPeak} Hz",
        )
    }

    @Test
    fun analyze_inputLargerThanFftSize_usesLastFftSizeSamples() {
        val analyzer = FftAnalyzer(fftSize = 512, sampleRate = sampleRate)
        // First half is silence, second half is a sine wave
        val samples = FloatArray(1024) { i ->
            if (i >= 512) sin(2.0 * PI * 1000.0 * (i - 512) / sampleRate).toFloat() * 0.5f else 0f
        }
        val result = analyzer.analyze(samples)
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
        var differences = 0
        for (i in 0 until 16) {
            if (abs(
                    (lowResult.fftChannels[i].toInt() and 0xFF) -
                        (highResult.fftChannels[i].toInt() and 0xFF),
                ) > 10
            ) {
                differences++
            }
        }
        assertTrue(differences > 0, "Different frequencies should produce different channel patterns")
    }

    @Test
    fun analyze_defaultFftSize_scalesPeakChannelIntoWledUpperRange() {
        val analyzer = FftAnalyzer()
        val samples = generateSineWave(
            frequency = 1000f,
            sampleRate = FftAnalyzer.DEFAULT_SAMPLE_RATE,
            count = FftAnalyzer.DEFAULT_FFT_SIZE,
            amplitude = 0.5f,
        )
        val result = analyzer.analyze(samples)
        val maxChannelLevel = result.fftChannels.maxOf { it.toInt() and 0xFF }
        assertTrue(
            maxChannelLevel > 150,
            "Expected peak GEQ channel to reach WLED upper byte range (> 150), got $maxChannelLevel",
        )
    }

    @Test
    fun analyze_whenSilent_mutesMagnitudeAndZeroesMajorPeak() {
        val analyzer = FftAnalyzer()
        val samples = generateSineWave(
            frequency = 1000f,
            sampleRate = FftAnalyzer.DEFAULT_SAMPLE_RATE,
            count = FftAnalyzer.DEFAULT_FFT_SIZE,
            amplitude = 0.5f,
        )
        val result = analyzer.analyze(samples, isSilent = true)
        assertEquals(0.001f, result.magnitude, 0.0001f)
        assertEquals(0f, result.majorPeak, 0.0001f)
    }

    @Test
    fun analyze_whenSilentAfterLoudFrame_decaysChannelsWithoutSnappingToZero() {
        val analyzer = FftAnalyzer()
        val samples = generateSineWave(
            frequency = 1000f,
            sampleRate = FftAnalyzer.DEFAULT_SAMPLE_RATE,
            count = FftAnalyzer.DEFAULT_FFT_SIZE,
            amplitude = 0.2f,
        )
        val activePeak = analyzer.analyze(samples, isSilent = false).fftChannels.maxOf { it.toInt() and 0xFF }
        val decayedPeak = analyzer.analyze(samples, isSilent = true).fftChannels.maxOf { it.toInt() and 0xFF }
        assertTrue(
            decayedPeak in 1 until activePeak,
            "Silent frame should decay GEQ channels smoothly (got $decayedPeak from $activePeak)",
        )
    }

    @Test
    fun analyze_oneKilohertzSine_peaksInChannelSevenAt44100And48000Hz() {
        listOf(44100, 48000).forEach { rate ->
            val analyzer = FftAnalyzer(fftSize = FftAnalyzer.DEFAULT_FFT_SIZE, sampleRate = rate)
            val samples = generateSineWave(
                frequency = 1000f,
                sampleRate = rate,
                count = FftAnalyzer.DEFAULT_FFT_SIZE,
                amplitude = 0.5f,
            )
            val result = analyzer.analyze(samples)
            val peakChannel = result.fftChannels.withIndex().maxByOrNull { it.value.toInt() and 0xFF }?.index ?: -1
            assertEquals(7, peakChannel, "1 kHz should center in GEQ channel 7 at $rate Hz, got channel $peakChannel")
        }
    }

    private fun generateSineWave(frequency: Float, sampleRate: Int, count: Int, amplitude: Float = 0.5f): FloatArray =
        FloatArray(count) { i ->
            sin(2.0 * PI * frequency * i / sampleRate).toFloat() * amplitude
        }
}
