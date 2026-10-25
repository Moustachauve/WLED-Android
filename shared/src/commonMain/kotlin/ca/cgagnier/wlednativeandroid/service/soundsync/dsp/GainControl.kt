package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.abs
import kotlin.math.min

/**
 * Gain control stage combining a manual gain slider with optional automatic
 * gain control (AGC).
 *
 * The manual gain is applied first, then AGC adjusts the overall level to
 * approach [targetLevel]. AGC uses a simple peak-tracking algorithm with
 * attack/release smoothing so gain changes are not jarring. AGC updates
 * are applied in chunks so that gain converges within a single large block.
 *
 * Output is hard-clipped to [-maxAmplitude, maxAmplitude] to prevent
 * distortion when gain is high.
 */
class GainControl(
    manualGain: Float = 1.0f,
    agcEnabled: Boolean = false,
    private val targetLevel: Float = DEFAULT_TARGET_LEVEL,
    private val maxAmplitude: Float = 1.0f,
) {
    private var _manualGain = manualGain.coerceIn(0f, MAX_MANUAL_GAIN)
    private var _agcEnabled = agcEnabled

    // AGC state
    private var agcGain = 1.0f
    private var peakEnvelope = 0f

    val manualGain: Float get() = _manualGain
    val agcEnabled: Boolean get() = _agcEnabled
    val currentAgcGain: Float get() = agcGain

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

            // Apply current gain to this chunk
            val totalGain = _manualGain * agcGain
            for (i in 0 until chunkSize) {
                val amplified = samples[offset + i] * totalGain
                output[offset + i] = amplified.coerceIn(-maxAmplitude, maxAmplitude)
            }

            // Update AGC for the next chunk
            if (_agcEnabled) {
                updateAgc(samples, offset, chunkSize)
            }

            offset += chunkSize
        }
        return output
    }

    fun setManualGain(gain: Float) {
        _manualGain = gain.coerceIn(0f, MAX_MANUAL_GAIN)
    }

    fun setAgcEnabled(enabled: Boolean) {
        _agcEnabled = enabled
        if (!enabled) {
            agcGain = 1.0f
            peakEnvelope = 0f
        }
    }

    /** Clears AGC state. Call when switching audio sources. */
    fun reset() {
        agcGain = 1.0f
        peakEnvelope = 0f
    }

    private fun updateAgc(samples: FloatArray, offset: Int, count: Int) {
        // Track the peak amplitude in this chunk
        var chunkPeak = 0f
        for (i in offset until offset + count) {
            val abs = abs(samples[i] * _manualGain)
            if (abs > chunkPeak) chunkPeak = abs
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
            agcGain = agcGain * AGC_SMOOTHING + desiredGain * (1f - AGC_SMOOTHING)
            agcGain = agcGain.coerceIn(MIN_AGC_GAIN, MAX_AGC_GAIN)
        }
    }

    companion object {
        const val DEFAULT_TARGET_LEVEL = 0.5f
        const val MAX_MANUAL_GAIN = 10.0f
        const val MIN_AGC_GAIN = 0.1f
        const val MAX_AGC_GAIN = 20.0f
        private const val AGC_UPDATE_INTERVAL = 256
        private const val AGC_RELEASE = 0.95f
        private const val AGC_SMOOTHING = 0.5f

        /** Minimum envelope level below which AGC gain is not updated. */
        private const val AGC_MIN_ENVELOPE = 0.001f
    }
}
