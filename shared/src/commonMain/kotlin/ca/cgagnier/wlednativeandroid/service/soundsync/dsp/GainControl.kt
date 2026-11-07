package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.abs
import kotlin.math.min

/**
 * Gain control stage combining a manual gain slider with optional automatic
 * gain control (AGC).
 *
 * When AGC is disabled, [manualGain] scales the input directly. When AGC is
 * enabled (and the manual gain slider is disabled in the UI), AGC tracks the
 * unscaled input peak envelope and adjusts gain automatically to approach
 * [targetLevel]. AGC updates are applied in chunks with attack/release smoothing
 * so gain converges smoothly within a single large block.
 *
 * Output is hard-clipped to [-maxAmplitude, maxAmplitude] to prevent
 * distortion when gain is high.
 */
class GainControl(
    manualGain: Float = 1.0f,
    agcEnabled: Boolean = false,
    private val targetLevel: Float = DEFAULT_TARGET_LEVEL,
    private val maxAmplitude: Float = 1.0f,
    private val manualGainMultiplier: Float = 1.0f,
) {
    var manualGain: Float = manualGain.coerceIn(0f, MAX_MANUAL_GAIN)
        private set
    var agcEnabled: Boolean = agcEnabled
        private set

    // AGC state
    private var agcGain = 1.0f
    private var peakEnvelope = 0f

    /**
     * Processes a block of audio samples through the gain stage.
     *
     * @param samples Input PCM samples.
     * @return Gain-adjusted and clipped samples, same length as input.
     */
    fun process(samples: FloatArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(0)

        val output = FloatArray(samples.size)
        var offset = 0
        while (offset < samples.size) {
            val chunkSize = min(AGC_UPDATE_INTERVAL, samples.size - offset)

            // Apply current gain to this chunk (use agcGain when AGC is active so a previously
            // saved manualGain value does not mute or clip AGC while the slider is disabled)
            val totalGain = if (agcEnabled) agcGain else manualGain * manualGainMultiplier
            for (i in 0 until chunkSize) {
                output[offset + i] = (samples[offset + i] * totalGain).coerceIn(-maxAmplitude, maxAmplitude)
            }

            // Update AGC for the next chunk
            if (agcEnabled) {
                updateAgc(samples, offset, chunkSize)
            }

            offset += chunkSize
        }
        return output
    }

    fun setManualGain(gain: Float) {
        manualGain = gain.coerceIn(0f, MAX_MANUAL_GAIN)
    }

    fun setAgcEnabled(enabled: Boolean) {
        agcEnabled = enabled
        if (!enabled) reset()
    }

    /** Clears AGC state. Call when switching audio sources. */
    fun reset() {
        agcGain = 1.0f
        peakEnvelope = 0f
    }

    private fun updateAgc(samples: FloatArray, offset: Int, count: Int) {
        // Track the unscaled peak amplitude in this chunk
        var chunkPeak = 0f
        for (i in offset until offset + count) {
            val amplitude = abs(samples[i])
            if (amplitude > chunkPeak) chunkPeak = amplitude
        }

        // Update envelope with attack/release
        peakEnvelope = if (chunkPeak > peakEnvelope) {
            chunkPeak // Attack: respond immediately to increases
        } else {
            peakEnvelope * AGC_RELEASE + chunkPeak * (1f - AGC_RELEASE)
        }

        // Calculate AGC gain to bring envelope toward target level
        if (peakEnvelope > AGC_MIN_ENVELOPE) {
            val desiredGain = targetLevel / peakEnvelope
            agcGain = (agcGain * AGC_SMOOTHING + desiredGain * (1f - AGC_SMOOTHING))
                .coerceIn(MIN_AGC_GAIN, MAX_AGC_GAIN)
        }
    }

    companion object {
        const val MAX_MANUAL_GAIN = 10.0f
        const val DEFAULT_TARGET_LEVEL = 0.75f
        private const val MIN_AGC_GAIN = 0.1f
        private const val MAX_AGC_GAIN = 32.0f
        private const val AGC_UPDATE_INTERVAL = 256
        private const val AGC_RELEASE = 0.95f
        private const val AGC_SMOOTHING = 0.5f

        /** Minimum envelope level below which AGC gain is not updated (noise gate). */
        private const val AGC_MIN_ENVELOPE = 0.005f
    }
}
