package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.cos
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
 * @param fftSize FFT window size (must be a power of 2). Default 1024 (~43 Hz/bin at 44.1 kHz,
 *   matching WLED's 512-sample FFT resolution at 22.05 kHz).
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

    // Scale factor converting normalized float FFT bin magnitudes to WLED's 16-bit FFT scale (0..4096)
    private val wledBinScale = WLED_FFT_SCALE_NUMERATOR / fftSize

    // Precomputed channel boundary bin indices
    private val channelBinRanges: List<IntRange> = computeChannelBinRanges()

    // Precomputed Hann window coefficients
    private val windowCoeffs: FloatArray = FloatArray(fftSize) { i ->
        (HANN_SCALE * (HANN_ONE - cos(TWO_PI * i / (fftSize - HANN_ONE_INT)))).toFloat()
    }

    // Scratch buffers for FFT (reused across calls)
    private val realBuffer = FloatArray(fftSize)
    private val imagBuffer = FloatArray(fftSize)
    private val magnitudes = FloatArray(numBins)

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

        // Fill real buffer with windowed samples (last fftSize samples), zero-padding if shorter
        val offset = maxOf(0, samples.size - fftSize)
        for (i in 0 until fftSize) {
            val sampleIndex = offset + i
            val sample = if (sampleIndex < samples.size) samples[sampleIndex] else 0f
            realBuffer[i] = sample * windowCoeffs[i]
            imagBuffer[i] = 0f
        }

        // Compute FFT in-place
        fft(realBuffer, imagBuffer)

        // Compute magnitude spectrum
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
        val wledMagnitude = maxMagnitude * wledBinScale

        // Map bins to 16 GEQ channels using WLED's pink-noise adjustment and square-root scaling
        val channels = ByteArray(FFT_CHANNEL_COUNT)
        for (ch in 0 until FFT_CHANNEL_COUNT) {
            val range = channelBinRanges[ch]
            var sum = 0f
            for (bin in range) {
                if (bin < numBins) {
                    sum += magnitudes[bin]
                }
            }
            val avgMagnitude = if (range.isEmpty()) 0f else sum / range.count() * wledBinScale
            channels[ch] = scaleChannelToWledRange(avgMagnitude, ch)
        }

        return FftResult(
            fftChannels = channels,
            magnitude = wledMagnitude,
            majorPeak = if (maxMagnitude > MIN_PEAK_MAGNITUDE) majorPeakFrequency else 0f,
        )
    }

    /**
     * Scales a channel's average WLED-normalized magnitude into `0..255` using WLED's
     * pink-noise equalization (`fftResultPink`) and default square-root scaling (`FFTScalingMode = 3`).
     */
    private fun scaleChannelToWledRange(avgWledMagnitude: Float, channelIndex: Int): Byte {
        var currentResult = avgWledMagnitude * FFT_RESULT_PINK[channelIndex] * FFT_DOWNSCALE
        currentResult = currentResult * SQRT_PRE_SCALE - SQRT_NOISE_OFFSET
        currentResult = if (currentResult > SQRT_MIN_THRESHOLD) sqrt(currentResult) else 0f
        currentResult *= SQRT_FREQ_BOOST_BASE + (channelIndex / SQRT_FREQ_BOOST_DIVISOR)
        val mapped = currentResult * (CHANNEL_MAX_VALUE / SQRT_DOMAIN_MAX)
        return mapped.toInt().coerceIn(0, CHANNEL_MAX_VALUE_INT).toByte()
    }

    /**
     * Maps FFT bins to 16 GEQ channels using WLED's frequency boundaries.
     * Each channel covers a logarithmic frequency range starting at bin 1 (skipping DC).
     */
    private fun computeChannelBinRanges(): List<IntRange> {
        var nextMinBin = 1
        val maxBinIndex = numBins - 1
        return GEQ_FREQ_BOUNDARIES.mapIndexed { index, upperFreq ->
            val lowerFreq = if (index == 0) 0f else GEQ_FREQ_BOUNDARIES[index - 1]
            val rawLowerBin = (lowerFreq / binFrequencyWidth).toInt().coerceIn(1, maxBinIndex)
            val rawUpperBin = (upperFreq / binFrequencyWidth).toInt().coerceIn(1, maxBinIndex)
            val lowerBin = maxOf(rawLowerBin, nextMinBin).coerceAtMost(maxBinIndex)
            val upperBin = maxOf(lowerBin, rawUpperBin).coerceAtMost(maxBinIndex)
            nextMinBin = (upperBin + 1).coerceAtMost(maxBinIndex)
            lowerBin..upperBin
        }
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
            for (k in 0 until halfSize) {
                val angle = angleStep * k
                val twiddleReal = cos(angle).toFloat()
                val twiddleImag = sin(angle).toFloat()

                for (i in 0 until n step size) {
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
        const val DEFAULT_FFT_SIZE = 1024
        const val DEFAULT_SAMPLE_RATE = 44100
        const val FFT_CHANNEL_COUNT = 16

        /** Divisor for Nyquist frequency (fftSize / 2). */
        private const val NYQUIST_DIVISOR = 2

        /** Number of bins at DC (always 1). */
        private const val DC_BIN_COUNT = 1

        /**
         * Numerator converting Hann-windowed float FFT magnitudes (where a full-scale sine
         * has peak `0.25 * fftSize`) to WLED's 16-bit FFT magnitude scale (`0..4096`).
         */
        private const val WLED_FFT_SCALE_NUMERATOR = 8192f

        /** Minimum raw FFT bin magnitude required to report a non-zero majorPeak frequency. */
        private const val MIN_PEAK_MAGNITUDE = 0.001f

        /** WLED window downscaling factor (`FFT_DOWNSCALE` in `audio_reactive.cpp`). */
        private const val FFT_DOWNSCALE = 0.46f

        /** WLED square-root scaling pre-multiplier (`FFTScalingMode = 3` in `audio_reactive.cpp`). */
        private const val SQRT_PRE_SCALE = 0.38f

        /** WLED square-root scaling noise floor offset (`-6.0f` in `audio_reactive.cpp`). */
        private const val SQRT_NOISE_OFFSET = 6.0f

        /** Minimum post-offset threshold before taking `sqrt` in WLED square-root mode. */
        private const val SQRT_MIN_THRESHOLD = 1.0f

        /** Base high-frequency compensation multiplier in WLED square-root mode. */
        private const val SQRT_FREQ_BOOST_BASE = 0.85f

        /** Per-channel high-frequency boost divisor in WLED square-root mode (`i / 4.5f`). */
        private const val SQRT_FREQ_BOOST_DIVISOR = 4.5f

        /** Upper domain bound (`sqrt(256) = 16.0`) mapped to `255` in WLED square-root mode. */
        private const val SQRT_DOMAIN_MAX = 16.0f

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

        /**
         * WLED pink-noise equalization multipliers (`fftResultPink` in `audio_reactive.cpp`)
         * that balance natural 1/f spectral rolloff across the 16 GEQ channels.
         */
        private val FFT_RESULT_PINK = floatArrayOf(
            1.70f, 1.71f, 1.73f, 1.78f, 1.68f, 1.56f, 1.55f, 1.63f,
            1.79f, 1.62f, 1.80f, 2.06f, 2.47f, 3.35f, 6.83f, 9.55f,
        )
    }
}
