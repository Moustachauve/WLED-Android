package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of FFT analysis, mapped to WLED's 16 GEQ (graphic equalizer) channels.
 *
 * @property fftChannels 16 frequency channel values, each 0-255.
 * @property magnitude Amplitude of the strongest frequency (FFT magnitude units).
 * @property majorPeak Frequency of the strongest bin, in Hz.
 */
data class FftResult(val fftChannels: ByteArray, val magnitude: Float, val majorPeak: Float) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FftResult) return false
        return fftChannels.contentEquals(other.fftChannels) &&
            magnitude == other.magnitude &&
            majorPeak == other.majorPeak
    }

    override fun hashCode(): Int {
        var result = fftChannels.contentHashCode()
        result = 31 * result + magnitude.hashCode()
        result = 31 * result + majorPeak.hashCode()
        return result
    }
}

/**
 * FFT analyzer that converts time-domain PCM samples into WLED's 16-channel
 * GEQ frequency representation.
 *
 * Uses an iterative radix-2 Cooley-Tukey FFT and maps frequency bins to
 * 16 logarithmic channels matching WLED audioreactive's frequency boundaries.
 *
 * @param fftSize FFT window size (must be a power of 2). Default 512.
 * @param sampleRate Audio sample rate in Hz. Default 44100.
 */
class FftAnalyzer(private val fftSize: Int = DEFAULT_FFT_SIZE, private val sampleRate: Int = DEFAULT_SAMPLE_RATE) {
    init {
        require(fftSize > 0 && fftSize.countOneBits() == 1) {
            "fftSize must be a power of 2, got $fftSize"
        }
    }

    private val binFrequencyWidth = sampleRate.toFloat() / fftSize
    private val numBins = fftSize / NYQUIST_DIVISOR + DC_BIN_COUNT

    // Precomputed channel boundary bin indices
    private val channelBinRanges: List<IntRange> = computeChannelBinRanges()

    // Precomputed Hann window coefficients
    private val windowCoeffs: FloatArray = FloatArray(fftSize) { i ->
        (HANN_SCALE * (HANN_ONE - cos(TWO_PI * i / (fftSize - HANN_ONE_INT)))).toFloat()
    }

    // Scratch buffers for FFT (reused across calls)
    private val realBuffer = FloatArray(fftSize)
    private val imagBuffer = FloatArray(fftSize)

    /**
     * Analyzes a block of audio samples and returns the 16-channel GEQ result.
     *
     * If [samples] is larger than [fftSize], only the last [fftSize] samples are used.
     * If smaller, the buffer is zero-padded.
     */
    fun analyze(samples: FloatArray): FftResult {
        if (samples.isEmpty()) {
            return FftResult(ByteArray(FFT_CHANNEL_COUNT), 0f, 0f)
        }

        // Fill real buffer with windowed samples (last fftSize samples)
        val offset = maxOf(0, samples.size - fftSize)
        val count = min(fftSize, samples.size)
        for (i in 0 until fftSize) {
            val sampleIndex = offset + i
            val sample = if (sampleIndex < samples.size) samples[sampleIndex] else 0f
            realBuffer[i] = sample * windowCoeffs[i]
            imagBuffer[i] = 0f
        }
        // Zero-pad the beginning if input was shorter than fftSize
        for (i in 0 until fftSize - count) {
            realBuffer[i] = 0f
            imagBuffer[i] = 0f
        }

        // Compute FFT in-place
        fft(realBuffer, imagBuffer)

        // Compute magnitude spectrum
        val magnitudes = FloatArray(numBins)
        for (i in 0 until numBins) {
            magnitudes[i] = sqrt(realBuffer[i] * realBuffer[i] + imagBuffer[i] * imagBuffer[i])
        }

        // Find the strongest bin (major peak)
        var maxMagnitude = 0f
        var maxBin = 0
        for (i in 1 until numBins) { // Skip DC bin
            if (magnitudes[i] > maxMagnitude) {
                maxMagnitude = magnitudes[i]
                maxBin = i
            }
        }
        val majorPeakFrequency = maxBin * binFrequencyWidth

        // Map bins to 16 GEQ channels
        val channels = ByteArray(FFT_CHANNEL_COUNT)
        for (ch in 0 until FFT_CHANNEL_COUNT) {
            val range = channelBinRanges[ch]
            var sum = 0f
            for (bin in range) {
                if (bin < numBins) {
                    sum += magnitudes[bin]
                }
            }
            // Scale to 0-255 range with logarithmic compression
            val avgMagnitude = if (range.isEmpty()) 0f else sum / range.count()
            val logDenominator = ln(1.0 + CHANNEL_SCALE * CHANNEL_MAX_INPUT)
            val scaled = (ln(1.0 + avgMagnitude * CHANNEL_SCALE) / logDenominator).toFloat()
            channels[ch] = (scaled * CHANNEL_MAX_VALUE).toInt().coerceIn(0, CHANNEL_MAX_VALUE_INT).toByte()
        }

        return FftResult(
            fftChannels = channels,
            magnitude = maxMagnitude,
            majorPeak = if (maxMagnitude > 0.001f) majorPeakFrequency else 0f,
        )
    }

    /**
     * Maps FFT bins to 16 GEQ channels using WLED's frequency boundaries.
     * Each channel covers a logarithmic frequency range.
     */
    private fun computeChannelBinRanges(): List<IntRange> = GEQ_FREQ_BOUNDARIES.mapIndexed { index, upperFreq ->
        val lowerFreq = if (index == 0) 0f else GEQ_FREQ_BOUNDARIES[index - 1]
        val lowerBin = (lowerFreq / binFrequencyWidth).toInt()
        val upperBin = (upperFreq / binFrequencyWidth).toInt().coerceAtMost(numBins - 1)
        lowerBin..upperBin
    }

    /**
     * Iterative radix-2 Cooley-Tukey FFT (in-place).
     * Computes the forward FFT of the complex signal in [real] + i*[imag].
     */
    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = fftSize

        // Bit-reversal permutation
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                // Swap real[i] <-> real[j]
                val tmp = real[i]
                real[i] = real[j]
                real[j] = tmp
                // Swap imag[i] <-> imag[j]
                val tmpIm = imag[i]
                imag[i] = imag[j]
                imag[j] = tmpIm
            }
        }

        // Butterfly operations
        var size = FFT_INITIAL_STAGE_SIZE
        while (size <= n) {
            val halfSize = size / FFT_STAGE_MULTIPLIER
            val angleStep = -TWO_PI / size
            for (i in 0 until n step size) {
                for (k in 0 until halfSize) {
                    val angle = angleStep * k
                    val twiddleReal = cos(angle).toFloat()
                    val twiddleImag = sin(angle).toFloat()

                    val evenReal = real[i + k]
                    val evenImag = imag[i + k]
                    val oddReal = real[i + k + halfSize] * twiddleReal - imag[i + k + halfSize] * twiddleImag
                    val oddImag = real[i + k + halfSize] * twiddleImag + imag[i + k + halfSize] * twiddleReal

                    real[i + k] = evenReal + oddReal
                    imag[i + k] = evenImag + oddImag
                    real[i + k + halfSize] = evenReal - oddReal
                    imag[i + k + halfSize] = evenImag - oddImag
                }
            }
            size *= FFT_STAGE_MULTIPLIER
        }
    }

    companion object {
        const val DEFAULT_FFT_SIZE = 512
        const val DEFAULT_SAMPLE_RATE = 44100
        const val FFT_CHANNEL_COUNT = 16

        /** Divisor for Nyquist frequency (fftSize / 2). */
        private const val NYQUIST_DIVISOR = 2

        /** Number of bins at DC (always 1). */
        private const val DC_BIN_COUNT = 1

        /** Maximum input magnitude used for logarithmic compression scaling. */
        private const val CHANNEL_MAX_INPUT = 100.0

        /** Maximum channel output value (0-255 range). */
        private const val CHANNEL_MAX_VALUE = 255f

        /** Maximum channel output value as Int for coerceIn. */
        private const val CHANNEL_MAX_VALUE_INT = 255

        /** Multiplier for FFT butterfly stage size (doubles each stage). */
        private const val FFT_STAGE_MULTIPLIER = 2

        /** Initial butterfly stage size for FFT. */
        private const val FFT_INITIAL_STAGE_SIZE = 2

        /** 2 * PI for angle calculations. */
        private const val TWO_PI = 2.0 * PI

        /** Hann window scale factor (0.5). */
        private const val HANN_SCALE = 0.5f

        /** Hann window constant (1.0). */
        private const val HANN_ONE = 1.0

        /** Hann window constant as Int (1). */
        private const val HANN_ONE_INT = 1

        /**
         * WLED GEQ channel upper frequency boundaries in Hz.
         * Channel 0 covers 0-55 Hz, channel 1 covers 55-77 Hz, etc.
         * These match the frequency mapping used by WLED audioreactive.
         */
        val GEQ_FREQ_BOUNDARIES = floatArrayOf(
            55f, 77f, 108f, 151f, 212f, 296f, 415f, 580f,
            811f, 1134f, 1587f, 2221f, 3108f, 4350f, 6087f, 8513f,
        )

        /** Scaling factor for channel magnitude compression. */
        private const val CHANNEL_SCALE = 10.0
    }
}
