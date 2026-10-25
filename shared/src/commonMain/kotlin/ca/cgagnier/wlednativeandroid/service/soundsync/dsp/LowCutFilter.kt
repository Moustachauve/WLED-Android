package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.PI
import kotlin.math.exp

/**
 * Second-order high-pass (low-cut) IIR filter for removing DC offset and
 * low-frequency rumble below the audible/musical range.
 *
 * Implemented as two cascaded one-pole high-pass stages for a -12 dB/octave
 * rolloff. Each stage uses: `y[n] = a * (y[n-1] + x[n] - x[n-1])`
 * where `a = exp(-2*pi*fc/fs)`.
 *
 * This matches the low-cut filtering that WLED audioreactive performs on-device
 * before FFT analysis, so that an external audio source doesn't need to worry
 * about infrasonic content.
 */
class LowCutFilter(
    private val cutoffHz: Float = DEFAULT_CUTOFF_HZ,
    private val sampleRate: Int = DEFAULT_SAMPLE_RATE,
) {
    private val alpha: Float = exp(-TWO_PI * cutoffHz / sampleRate).toFloat()

    // Two cascaded stages, each with its own state
    private var prevInput1 = 0f
    private var prevOutput1 = 0f
    private var prevInput2 = 0f
    private var prevOutput2 = 0f

    /**
     * Processes a block of audio samples through the high-pass filter.
     * The filter is stateful — it remembers the previous input/output across calls.
     *
     * @param samples Input PCM samples (normalized, roughly -1.0 to 1.0).
     * @return Filtered samples, same length as input.
     */
    fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)
        val output = FloatArray(samples.size)
        var x1 = prevInput1
        var y1 = prevOutput1
        var x2 = prevInput2
        var y2 = prevOutput2
        for (i in samples.indices) {
            // Stage 1
            val x = samples[i]
            val stage1 = alpha * (y1 + x - x1)
            x1 = x
            y1 = stage1
            // Stage 2
            val stage2 = alpha * (y2 + stage1 - x2)
            x2 = stage1
            y2 = stage2
            output[i] = stage2
        }
        prevInput1 = x1
        prevOutput1 = y1
        prevInput2 = x2
        prevOutput2 = y2
        return output
    }

    /** Clears the filter's internal state. Call when switching audio sources. */
    fun reset() {
        prevInput1 = 0f
        prevOutput1 = 0f
        prevInput2 = 0f
        prevOutput2 = 0f
    }

    companion object {
        const val DEFAULT_CUTOFF_HZ = 55f
        const val DEFAULT_SAMPLE_RATE = 44100

        /** 2 * PI for filter coefficient calculation. */
        private const val TWO_PI = 2.0 * PI
    }
}
