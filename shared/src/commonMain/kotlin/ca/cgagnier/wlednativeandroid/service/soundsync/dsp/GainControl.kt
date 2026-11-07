package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min

/**
 * Gain control stage combining a manual gain slider with optional WLED-style
 * two-zone automatic gain control (AGC).
 *
 * When [agcEnabled] is `false`, [manualGain] scales the input directly. When
 * [agcEnabled] is `true`, AGC tracks the unscaled input peak envelope with a slow
 * peak hold ([AgcMode.peakDecayTimeSec]) using the selected [agcMode] (`NORMAL`,
 * `VIVID`, or `LAZY`) and adjusts gain using two speed zones:
 * - **Normal zone** (`[zoneLow]..[zoneHigh]`): Gain glides slowly ([AgcMode.slowFollowTimeSec])
 *   toward [AgcMode.targetLevel] so musical dynamics between beats and phrases are preserved.
 * - **Emergency zone** (`< zoneLow` or `> zoneHigh`): Gain adapts quickly
 *   ([AgcMode.fastFollowTimeSec]) to prevent sustained clipping or dead LEDs.
 * - **Squelch freeze**: When the unscaled signal is at or below [squelchAmplitude],
 *   AGC holds its gain steady instead of amplifying background room noise.
 *
 * Output is hard-clipped to `[-MAX_AMPLITUDE, MAX_AMPLITUDE]` to prevent distortion.
 */
class GainControl(
    manualGain: Float = 1.0f,
    agcEnabled: Boolean = false,
    agcMode: AgcMode = AgcMode.NORMAL,
    sampleRate: Int = FftAnalyzer.DEFAULT_SAMPLE_RATE,
    squelchAmplitude: Float = DEFAULT_SQUELCH_AMPLITUDE,
    private val manualGainMultiplier: Float = 1.0f,
) {
    var manualGain: Float = manualGain.coerceIn(0f, MAX_MANUAL_GAIN)
        private set
    var agcEnabled: Boolean = agcEnabled
        private set
    var agcMode: AgcMode = agcMode
        private set

    private var currentSampleRate: Int = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
    private var currentSquelchAmplitude: Float = squelchAmplitude.coerceAtLeast(MIN_SQUELCH_AMPLITUDE)
    private val safeGainMultiplier: Float = manualGainMultiplier.coerceAtLeast(MIN_GAIN_MULTIPLIER)

    // AGC state
    private var agcGain = safeGainMultiplier
    private var peakEnvelope = 0f

    /**
     * Effective gain on the `0.0..MAX_MANUAL_GAIN` UI slider scale.
     *
     * Returns [manualGain] when AGC is disabled, or the active automatic gain mapped
     * to the slider range when AGC is enabled.
     */
    val effectiveGain: Float
        get() = if (agcEnabled) mappedAgcGain() else manualGain

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
                output[offset + i] = (samples[offset + i] * totalGain).coerceIn(-MAX_AMPLITUDE, MAX_AMPLITUDE)
            }

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
        if (!enabled) {
            reset()
        }
    }

    fun setAgcMode(mode: AgcMode) {
        agcMode = mode
    }

    fun setSampleRate(sampleRate: Int) {
        currentSampleRate = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
    }

    fun setSquelchAmplitude(amplitude: Float) {
        currentSquelchAmplitude = amplitude.coerceAtLeast(MIN_SQUELCH_AMPLITUDE)
    }

    /** Clears AGC state. Call when switching audio sources. */
    fun reset() {
        agcGain = safeGainMultiplier
        peakEnvelope = 0f
    }

    private fun mappedAgcGain(): Float = (agcGain / safeGainMultiplier).coerceIn(0f, MAX_MANUAL_GAIN)

    private fun updateAgc(samples: FloatArray, offset: Int, count: Int) {
        val mode = agcMode

        var chunkPeak = 0f
        for (i in offset until offset + count) {
            val amplitude = abs(samples[i])
            if (amplitude > chunkPeak) chunkPeak = amplitude
        }

        val dtSec = count.toFloat() / currentSampleRate
        val peakDecayRetention = exp(-dtSec / mode.peakDecayTimeSec)

        // When signal is at or below the squelch threshold, slowly decay peakEnvelope
        // and hold agcGain steady so ambient room noise is not amplified to MAX_AGC_GAIN.
        if (chunkPeak <= currentSquelchAmplitude) {
            peakEnvelope *= peakDecayRetention
            if (peakEnvelope < MIN_PEAK_ENVELOPE) {
                peakEnvelope = 0f
            }
            return
        }

        peakEnvelope = when {
            peakEnvelope <= currentSquelchAmplitude -> chunkPeak

            chunkPeak > peakEnvelope -> peakEnvelope + PEAK_ATTACK_BLEND * (chunkPeak - peakEnvelope)

            peakEnvelope * agcGain > OVERDRIVE_RECOVERY_CEILING ->
                peakEnvelope + PEAK_ATTACK_BLEND * (chunkPeak - peakEnvelope)

            else -> peakEnvelope * peakDecayRetention
        }

        if (peakEnvelope > MIN_PEAK_ENVELOPE) {
            val desiredGain = (mode.targetLevel / peakEnvelope).coerceIn(MIN_AGC_GAIN, MAX_AGC_GAIN)
            val amplifiedPeak = chunkPeak * agcGain
            val lowEmergencyThreshold = (currentSquelchAmplitude + mode.zoneLow)
                .coerceAtMost(mode.targetLevel * LOW_ZONE_TARGET_CAP_RATIO)
            val inEmergencyZone = amplifiedPeak > mode.zoneHigh || amplifiedPeak < lowEmergencyThreshold
            val followTimeSec = if (inEmergencyZone) mode.fastFollowTimeSec else mode.slowFollowTimeSec
            val gainRetention = exp(-dtSec / followTimeSec)
            agcGain = (agcGain * gainRetention + desiredGain * (1f - gainRetention))
                .coerceIn(MIN_AGC_GAIN, MAX_AGC_GAIN)
        }
    }

    companion object {
        const val MAX_MANUAL_GAIN = 30.0f
        const val DEFAULT_SQUELCH_AMPLITUDE = 0.005f
        private const val MAX_AMPLITUDE = 1.0f
        internal const val MIN_SQUELCH_AMPLITUDE = 0.0001f
        private const val MIN_SAMPLE_RATE = 8000
        private const val MIN_GAIN_MULTIPLIER = 0.001f
        private const val MIN_AGC_GAIN = 0.1f
        private const val MAX_AGC_GAIN = 90.0f
        private const val AGC_UPDATE_INTERVAL = 256
        private const val PEAK_ATTACK_BLEND = 0.5f
        private const val OVERDRIVE_RECOVERY_CEILING = 1.25f
        private const val LOW_ZONE_TARGET_CAP_RATIO = 0.5f
        private const val MIN_PEAK_ENVELOPE = 0.0001f
    }
}
