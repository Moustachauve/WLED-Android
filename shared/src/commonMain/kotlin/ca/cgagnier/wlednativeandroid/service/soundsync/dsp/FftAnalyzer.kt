package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of FFT analysis, mapped to WLED's 16 GEQ (graphic equalizer) channels.
 *
 * @property fftChannels 16 frequency channel values, each 0-254.
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

    // Scale factor converting normalized float FFT bin magnitudes into WLED's channel scale (0..4096)
    private val wledChannelBinScale = WLED_CHANNEL_SCALE_NUMERATOR / fftSize

    // Precomputed channel boundary bin indices
    private val channelBinRanges: List<IntRange> = computeChannelBinRanges()

    // Precomputed Hann window coefficients
    private val windowCoeffs: FloatArray = FloatArray(fftSize) { i ->
        (HANN_SCALE * (HANN_ONE - cos(TWO_PI * i / (fftSize - HANN_ONE_INT)))).toFloat()
    }

    // Scratch buffers for FFT and linear-domain channel smoothing (reused across calls)
    private val realBuffer = FloatArray(fftSize)
    private val imagBuffer = FloatArray(fftSize)
    private val magnitudes = FloatArray(numBins)
    private val smoothedLinearChannels = FloatArray(FFT_CHANNEL_COUNT)

    /**
     * Analyzes a block of audio samples and returns the 16-channel GEQ result.
     *
     * If [samples] is larger than [fftSize], only the last [fftSize] samples are used.
     * If smaller, the buffer is zero-padded.
     *
     * @param samples Input PCM samples.
     * @param isSilent When `true` (noise gate closed), decays linear channel state toward zero
     *   and mutes [FftResult.magnitude] (`0.001f`) to match WLED's `audio_reactive.cpp` gate.
     */
    fun analyze(samples: FloatArray, isSilent: Boolean = false): FftResult {
        if (samples.isEmpty()) {
            return FftResult(ByteArray(FFT_CHANNEL_COUNT), 0f, 0f)
        }
        if (isSilent) {
            return decaySilentResult()
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
        val wledMagnitude = maxMagnitude * wledChannelBinScale * WLED_RAW_MAGNITUDE_RATIO

        return FftResult(
            fftChannels = computeSmoothedGeqChannels(),
            magnitude = wledMagnitude,
            majorPeak = if (maxMagnitude > MIN_PEAK_MAGNITUDE) majorPeakFrequency else 0f,
        )
    }

    /** Clears linear-domain GEQ channel smoothing state. */
    fun reset() {
        smoothedLinearChannels.fill(0f)
    }

    private fun decaySilentResult(): FftResult {
        val channels = ByteArray(FFT_CHANNEL_COUNT)
        for (ch in 0 until FFT_CHANNEL_COUNT) {
            var decayed = smoothedLinearChannels[ch] * SILENCE_CHANNEL_DECAY
            if (decayed < MIN_LINEAR_CHANNEL_MAGNITUDE) decayed = 0f
            smoothedLinearChannels[ch] = decayed
            channels[ch] = scaleChannelToWledRange(decayed, ch)
        }
        return FftResult(
            fftChannels = channels,
            magnitude = MUTED_FFT_MAGNITUDE,
            majorPeak = 0f,
        )
    }

    private fun computeSmoothedGeqChannels(): ByteArray {
        val channels = ByteArray(FFT_CHANNEL_COUNT)
        for (ch in 0 until FFT_CHANNEL_COUNT) {
            val range = channelBinRanges[ch]
            var sum = 0f
            for (bin in range) {
                sum += magnitudes[bin]
            }
            val avgMagnitude = sum / range.count() * wledChannelBinScale * GEQ_CHANNEL_DAMPING[ch]
            val currentLinear = avgMagnitude * FFT_RESULT_PINK[ch] * FFT_DOWNSCALE
            val previousLinear = smoothedLinearChannels[ch]
            val smoothedLinear = if (currentLinear >= previousLinear) {
                currentLinear * FFT_RISE_WEIGHT + previousLinear * (1f - FFT_RISE_WEIGHT)
            } else {
                currentLinear * FFT_FALL_WEIGHT + previousLinear * (1f - FFT_FALL_WEIGHT)
            }
            smoothedLinearChannels[ch] = if (smoothedLinear < MIN_LINEAR_CHANNEL_MAGNITUDE) 0f else smoothedLinear
            channels[ch] = scaleChannelToWledRange(smoothedLinearChannels[ch], ch)
        }
        return channels
    }

    /**
     * Scales a channel's smoothed linear WLED magnitude into `0..254` using WLED's
     * default square-root scaling (`FFTScalingMode = 3` in `postProcessFFTResults`).
     */
    private fun scaleChannelToWledRange(linearChannelMagnitude: Float, channelIndex: Int): Byte {
        var currentResult = linearChannelMagnitude * SQRT_PRE_SCALE - SQRT_NOISE_OFFSET
        currentResult = if (currentResult > SQRT_MIN_THRESHOLD) sqrt(currentResult) else 0f
        currentResult *= SQRT_FREQ_BOOST_BASE + (channelIndex / SQRT_FREQ_BOOST_DIVISOR)
        val mapped = currentResult * (CHANNEL_MAX_VALUE / SQRT_DOMAIN_MAX)
        return mapped.toInt().coerceIn(0, CHANNEL_TRANSMIT_MAX_INT).toByte()
    }

    /**
     * Maps FFT bins to 16 GEQ channels using WLED's inclusive overlapping frequency boundaries.
     * Each channel covers a logarithmic frequency range starting at bin 1 (skipping DC).
     */
    private fun computeChannelBinRanges(): List<IntRange> {
        val maxBinIndex = numBins - 1
        return GEQ_FREQ_BOUNDARIES.indices.map { index ->
            val lowerFreq = GEQ_LOWER_FREQ_BOUNDARIES[index]
            val upperFreq = GEQ_FREQ_BOUNDARIES[index]
            val lowerBin = (lowerFreq / binFrequencyWidth).roundToInt().coerceIn(1, maxBinIndex)
            val upperBin = maxOf(lowerBin, (upperFreq / binFrequencyWidth).roundToInt().coerceIn(1, maxBinIndex))
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
         * Numerator converting Hann-windowed float FFT bin magnitudes (where a full-scale sine
         * has peak `0.25 * fftSize`) to WLED's averaged GEQ channel scale (`0..4096` after `/16`
         * in `fftAddAvg`, with a full-scale sine mapping to `0.25 * 16384 = 4096`).
         */
        private const val WLED_CHANNEL_SCALE_NUMERATOR = 16384f

        /**
         * Ratio between WLED's raw `FFT_Magnitude` peak and the `/16` averaged GEQ channel
         * domain (`fftAddAvg` divides by 16 while `FFT_Magnitude` remains unscaled).
         */
        private const val WLED_RAW_MAGNITUDE_RATIO = 16f

        /** Minimum raw FFT bin magnitude required to report a non-zero majorPeak frequency. */
        private const val MIN_PEAK_MAGNITUDE = 0.001f

        /** Muted `FFT_Magnitude` emitted when the volume noise gate is closed (`audio_reactive.cpp`). */
        private const val MUTED_FFT_MAGNITUDE = 0.001f

        /**
         * WLED window downscaling factor (`FFT_DOWNSCALE = 0.40f` in `audio_reactive.cpp`
         * when `FFT_PREFER_EXACT_PEAKS` is enabled).
         */
        private const val FFT_DOWNSCALE = 0.40f

        /** WLED fast-rise weight for linear GEQ channels (`0.75f` in `postProcessFFTResults`). */
        private const val FFT_RISE_WEIGHT = 0.75f

        /** WLED decay weight for linear GEQ channels (`0.22f` in `postProcessFFTResults`). */
        private const val FFT_FALL_WEIGHT = 0.22f

        /** WLED channel decay factor per frame when the noise gate is closed (`0.85f` in `audio_reactive.cpp`). */
        private const val SILENCE_CHANNEL_DECAY = 0.85f

        /** Minimum linear channel magnitude before snapping to zero (`4.0f` in `audio_reactive.cpp`). */
        private const val MIN_LINEAR_CHANNEL_MAGNITUDE = 4.0f

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

        /** Maximum channel output value (0-255 scale factor used by `mapf`). */
        private const val CHANNEL_MAX_VALUE = 255f

        /** Maximum transmitted byte value for `fftResult` (`constrain(fftResult[i], 0, 254)` in WLED). */
        private const val CHANNEL_TRANSMIT_MAX_INT = 254

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
         * WLED GEQ channel lower frequency boundaries in Hz (`fftAddAvg` start bins at 22.05 kHz).
         */
        private val GEQ_LOWER_FREQ_BOUNDARIES = floatArrayOf(
            43f, 86f, 129f, 216f, 301f, 430f, 560f, 818f,
            1120f, 1421f, 1895f, 2412f, 3015f, 3704f, 4479f, 7106f,
        )

        /**
         * WLED GEQ channel upper frequency boundaries in Hz (`fftAddAvg` end bins at 22.05 kHz).
         * Channel 0 covers 43-86 Hz, channel 1 covers 86-129 Hz, etc., with 1 kHz centered in channel 7.
         * Matches the frequency mapping used by WLED (`usermods/audioreactive_v1/audio_reactive.cpp`).
         */
        val GEQ_FREQ_BOUNDARIES = floatArrayOf(
            86f, 129f, 216f, 301f, 430f, 560f, 818f, 1120f,
            1421f, 1895f, 2412f, 3015f, 3704f, 4479f, 7106f, 9259f,
        )

        /**
         * Per-channel pre-pink damping factors from WLED's `audio_reactive.cpp`
         * (`0.88f` on channel 14 and `0.70f` on channel 15).
         */
        private val GEQ_CHANNEL_DAMPING = floatArrayOf(
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
            1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0.88f, 0.70f,
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
