package ca.cgagnier.wlednativeandroid.service.audio

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Processes raw PCM audio samples into WLED-compatible audio data:
 * volume levels, peak detection, and 16 GEQ frequency bands.
 */
class AudioProcessor(
    private val sampleRate: Int = SAMPLE_RATE,
) {
    private var smoothedVolume = 0.0f
    private var peakThreshold = 40.0f
    private var lastPeakTime = 0L

    // Hanning window, pre-computed for FFT_SIZE
    private val window = FloatArray(FFT_SIZE) { i ->
        (0.5 * (1 - kotlin.math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
    }

    /**
     * Process a chunk of 16-bit PCM samples and return the analysis result.
     * [samples] should contain at least [FFT_SIZE] values.
     */
    fun process(samples: ShortArray, gain: Float = 1.0f): AudioData {
        val count = min(samples.size, FFT_SIZE)

        // Convert to float, apply gain and window
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        var sumSquares = 0.0f
        for (i in 0 until count) {
            val normalized = samples[i] / 32768.0f * gain
            real[i] = normalized * window[i]
            sumSquares += normalized * normalized
        }

        // RMS volume scaled to 0-255 range
        val rms = sqrt(sumSquares / count)
        val rawVolume = min(rms * VOLUME_SCALE, 255.0f)

        // Smoothed volume (exponential moving average)
        smoothedVolume = smoothedVolume * SMOOTHING_FACTOR + rawVolume * (1.0f - SMOOTHING_FACTOR)

        // Peak detection
        val now = System.currentTimeMillis()
        val isPeak = rawVolume > peakThreshold && (now - lastPeakTime) > PEAK_COOLDOWN_MS
        if (isPeak) {
            lastPeakTime = now
        }

        // Adaptive peak threshold
        peakThreshold = peakThreshold * 0.995f + rawVolume * 0.005f
        peakThreshold = max(peakThreshold, MIN_PEAK_THRESHOLD)

        // FFT
        FFT.compute(real, imag)

        // Compute magnitude spectrum (only first half is useful)
        val magnitudes = FloatArray(FFT_SIZE / 2)
        for (i in magnitudes.indices) {
            magnitudes[i] = sqrt(real[i] * real[i] + imag[i] * imag[i])
        }

        // Map to 16 GEQ frequency bands
        val fftResult = IntArray(GEQ_CHANNELS)
        var maxMagnitude = 0.0f
        var majorPeakBin = 0
        val binWidth = sampleRate.toFloat() / FFT_SIZE

        for (ch in 0 until GEQ_CHANNELS) {
            val startBin = max(1, (FREQ_BANDS[ch][0] / binWidth).toInt())
            val endBin = min(FFT_SIZE / 2 - 1, (FREQ_BANDS[ch][1] / binWidth).toInt())

            var bandSum = 0.0f
            var bandMax = 0.0f
            var binCount = 0
            for (bin in startBin..endBin) {
                bandSum += magnitudes[bin]
                if (magnitudes[bin] > bandMax) {
                    bandMax = magnitudes[bin]
                }
                if (magnitudes[bin] > maxMagnitude) {
                    maxMagnitude = magnitudes[bin]
                    majorPeakBin = bin
                }
                binCount++
            }

            // Use a mix of average and peak for the band value
            val bandValue = if (binCount > 0) {
                (bandSum / binCount * 0.5f + bandMax * 0.5f) * FFT_SCALE
            } else {
                0.0f
            }
            fftResult[ch] = min(254, max(0, bandValue.toInt()))
        }

        val majorPeakFreq = majorPeakBin * binWidth
        val clampedPeakFreq = majorPeakFreq.coerceIn(1.0f, 11025.0f)

        return AudioData(
            sampleRaw = rawVolume,
            sampleSmth = smoothedVolume,
            samplePeak = if (isPeak) 1 else 0,
            fftResult = fftResult,
            fftMagnitude = maxMagnitude,
            fftMajorPeak = clampedPeakFreq,
        )
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val FFT_SIZE = 1024
        const val GEQ_CHANNELS = 16

        private const val VOLUME_SCALE = 512.0f
        private const val FFT_SCALE = 800.0f
        private const val SMOOTHING_FACTOR = 0.7f
        private const val PEAK_COOLDOWN_MS = 80L
        private const val MIN_PEAK_THRESHOLD = 20.0f

        // 16 GEQ frequency band boundaries in Hz (matching WLED AudioReactive)
        private val FREQ_BANDS = arrayOf(
            floatArrayOf(43f, 86f),
            floatArrayOf(86f, 129f),
            floatArrayOf(129f, 216f),
            floatArrayOf(216f, 301f),
            floatArrayOf(301f, 430f),
            floatArrayOf(430f, 560f),
            floatArrayOf(560f, 818f),
            floatArrayOf(818f, 1120f),
            floatArrayOf(1120f, 1421f),
            floatArrayOf(1421f, 1895f),
            floatArrayOf(1895f, 2412f),
            floatArrayOf(2412f, 3015f),
            floatArrayOf(3015f, 3704f),
            floatArrayOf(3704f, 4479f),
            floatArrayOf(4479f, 7106f),
            floatArrayOf(7106f, 9259f),
        )
    }
}

data class AudioData(
    val sampleRaw: Float,
    val sampleSmth: Float,
    val samplePeak: Int,
    val fftResult: IntArray,
    val fftMagnitude: Float,
    val fftMajorPeak: Float,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AudioData) return false
        return sampleRaw == other.sampleRaw &&
            sampleSmth == other.sampleSmth &&
            samplePeak == other.samplePeak &&
            fftResult.contentEquals(other.fftResult) &&
            fftMagnitude == other.fftMagnitude &&
            fftMajorPeak == other.fftMajorPeak
    }

    override fun hashCode(): Int {
        var result = sampleRaw.hashCode()
        result = 31 * result + sampleSmth.hashCode()
        result = 31 * result + samplePeak
        result = 31 * result + fftResult.contentHashCode()
        result = 31 * result + fftMagnitude.hashCode()
        result = 31 * result + fftMajorPeak.hashCode()
        return result
    }
}
