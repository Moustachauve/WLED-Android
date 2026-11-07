package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlin.math.PI
import kotlin.math.sin

/**
 * Shared visualizer math for the 5-bar Sound Sync toolbar icon across Android and iOS.
 *
 * Computes normalized vertical bar height ratios (`0.2f..1.0f`) matching the 5 centered
 * bars of the `graphic_eq` / `waveform` icon, combining a gentle sinusoidal idle wave
 * during silence with live 5-band FFT channel levels when audio plays.
 */
object SoundSyncIconVisualizer {
    const val BAR_COUNT = 5
    const val MIN_BAR_LEVEL = 0.2f
    const val MID_RESTING_BAR_LEVEL = 0.6f
    const val MAX_BAR_LEVEL = 1.0f
    const val IDLE_MAX_BAR_LEVEL = 0.5f

    const val ATTACK_SMOOTHING = 0.55f
    const val DECAY_SMOOTHING = 0.2f

    private const val IDLE_WAVE_PERIOD_MS = 1200f
    private const val IDLE_BAR_PHASE_STEP = 0.9f
    private const val WAVE_NORMALIZATION_SCALE = 0.5f
    private const val TWO_PI_FLOAT = (2.0 * PI).toFloat()

    private const val BAND_0_END = 2
    private const val BAND_1_START = 3
    private const val BAND_1_END = 5
    private const val BAND_2_START = 6
    private const val BAND_2_END = 9
    private const val BAND_3_START = 10
    private const val BAND_3_END = 12
    private const val BAND_4_START = 13
    private const val BAND_4_END = 15

    private const val BYTE_MASK = 0xFF
    private const val CHANNEL_MAX_VALUE = 255f

    private val restingLevels = floatArrayOf(
        MIN_BAR_LEVEL,
        MID_RESTING_BAR_LEVEL,
        MAX_BAR_LEVEL,
        MID_RESTING_BAR_LEVEL,
        MIN_BAR_LEVEL,
    )

    private val bandStarts = intArrayOf(
        0,
        BAND_1_START,
        BAND_2_START,
        BAND_3_START,
        BAND_4_START,
    )

    private val bandEndsInclusive = intArrayOf(
        BAND_0_END,
        BAND_1_END,
        BAND_2_END,
        BAND_3_END,
        BAND_4_END,
    )

    /**
     * Returns the resting height ratio (`0.2f..1.0f`) for [barIndex] (`0 until BAR_COUNT`).
     */
    fun restingBarLevel(barIndex: Int): Float = restingLevels[barIndex.coerceIn(0, BAR_COUNT - 1)]

    /**
     * Returns the first FFT channel index (`0..15`) mapped to [barIndex].
     */
    fun bandChannelStart(barIndex: Int): Int = bandStarts[barIndex.coerceIn(0, BAR_COUNT - 1)]

    /**
     * Returns the last FFT channel index (`0..15`, inclusive) mapped to [barIndex].
     */
    fun bandChannelEndInclusive(barIndex: Int): Int = bandEndsInclusive[barIndex.coerceIn(0, BAR_COUNT - 1)]

    /**
     * Computes the target normalized height (`0.2f..1.0f`) for a single bar given its
     * peak unsigned FFT channel value (`0..255`) and elapsed animation time in milliseconds.
     */
    fun computeBarLevel(barIndex: Int, isRunning: Boolean, bandPeakChannelValue: Int, elapsedTimeMs: Long): Float {
        val clampedIndex = barIndex.coerceIn(0, BAR_COUNT - 1)
        if (!isRunning) {
            return restingLevels[clampedIndex]
        }

        val basePhase = elapsedTimeMs / IDLE_WAVE_PERIOD_MS * TWO_PI_FLOAT
        val wave = (sin(basePhase - clampedIndex * IDLE_BAR_PHASE_STEP) + 1f) * WAVE_NORMALIZATION_SCALE
        val idleLevel = MIN_BAR_LEVEL + wave * (IDLE_MAX_BAR_LEVEL - MIN_BAR_LEVEL)

        val bandNormalized = (bandPeakChannelValue / CHANNEL_MAX_VALUE).coerceIn(0f, 1f)
        val audioLevel = MIN_BAR_LEVEL + bandNormalized * (MAX_BAR_LEVEL - MIN_BAR_LEVEL)

        return maxOf(idleLevel, audioLevel).coerceIn(MIN_BAR_LEVEL, MAX_BAR_LEVEL)
    }

    /**
     * Computes the target normalized heights (`0.2f..1.0f`) for all 5 bars into [output].
     */
    fun computeBarLevels(
        isRunning: Boolean,
        fftChannels: ByteArray,
        elapsedTimeMs: Long,
        output: FloatArray = FloatArray(BAR_COUNT),
    ): FloatArray {
        require(output.size >= BAR_COUNT) {
            "output must have at least $BAR_COUNT elements"
        }

        for (i in 0 until BAR_COUNT) {
            val peakChannelValue = if (isRunning) {
                computeBandPeakChannelValue(fftChannels, bandStarts[i], bandEndsInclusive[i])
            } else {
                0
            }
            output[i] = computeBarLevel(
                barIndex = i,
                isRunning = isRunning,
                bandPeakChannelValue = peakChannelValue,
                elapsedTimeMs = elapsedTimeMs,
            )
        }
        return output
    }

    /**
     * Smooths transitions between the [previous] and [target] bar levels using asymmetric
     * attack/decay factors when [isRunning] is `true`, or snaps to [target] when stopped.
     */
    fun smoothBarLevel(previous: Float, target: Float, isRunning: Boolean): Float {
        if (!isRunning) return target
        val factor = if (target >= previous) ATTACK_SMOOTHING else DECAY_SMOOTHING
        return previous + (target - previous) * factor
    }

    private fun computeBandPeakChannelValue(fftChannels: ByteArray, startChannel: Int, endChannelInclusive: Int): Int {
        if (fftChannels.isEmpty()) return 0
        var maxChannelValue = 0
        for (ch in startChannel..endChannelInclusive) {
            if (ch < fftChannels.size) {
                val value = fftChannels[ch].toInt() and BYTE_MASK
                if (value > maxChannelValue) {
                    maxChannelValue = value
                }
            }
        }
        return maxChannelValue
    }
}
