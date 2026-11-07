package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * Shared visualizer math for the 5-bar Sound Sync toolbar icon and 16-channel frequency
 * visualization chart across Android and iOS.
 *
 * Computes normalized vertical bar height ratios (`0.2f..1.0f`) matching the 5 centered
 * bars of the `graphic_eq` / `waveform` icon, combining a gentle sinusoidal idle wave
 * during silence with live 5-band FFT channel levels when audio plays, and provides
 * frame-rate-independent smoothing for both toolbar and chart bars.
 */
object SoundSyncIconVisualizer {
    const val BAR_COUNT = 5
    const val MIN_BAR_LEVEL = 0.2f
    const val MID_RESTING_BAR_LEVEL = 0.6f
    const val MAX_BAR_LEVEL = 1.0f
    const val IDLE_MAX_BAR_LEVEL = 0.5f

    const val ATTACK_SMOOTHING = 0.55f
    const val DECAY_SMOOTHING = 0.2f

    /** Reference 60 Hz frame duration in milliseconds so smoothing speed is independent of display FPS. */
    const val REFERENCE_FRAME_MS = 16.667f
    private const val MAX_FRAME_MULTIPLIER = 4f

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
    fun restingBarLevel(barIndex: Int): Float = restingLevels[barIndex]

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

        if (!isRunning) {
            for (i in 0 until BAR_COUNT) {
                output[i] = restingLevels[i]
            }
            return output
        }

        val basePhase = elapsedTimeMs / IDLE_WAVE_PERIOD_MS * TWO_PI_FLOAT
        for (i in 0 until BAR_COUNT) {
            val wave = (sin(basePhase - i * IDLE_BAR_PHASE_STEP) + 1f) * WAVE_NORMALIZATION_SCALE
            val idleLevel = MIN_BAR_LEVEL + wave * (IDLE_MAX_BAR_LEVEL - MIN_BAR_LEVEL)

            val peakChannelValue = computeBandPeakChannelValue(
                fftChannels = fftChannels,
                startChannel = bandStarts[i],
                endChannelInclusive = bandEndsInclusive[i],
            )
            val bandNormalized = (peakChannelValue / CHANNEL_MAX_VALUE).coerceIn(0f, 1f)
            val audioLevel = MIN_BAR_LEVEL + bandNormalized * (MAX_BAR_LEVEL - MIN_BAR_LEVEL)

            output[i] = maxOf(idleLevel, audioLevel).coerceIn(MIN_BAR_LEVEL, MAX_BAR_LEVEL)
        }
        return output
    }

    /**
     * Smooths transitions between the [previous] and [target] bar levels using time-adjusted
     * asymmetric attack/decay factors when [isRunning] is `true`, or snaps to [target] when stopped.
     *
     * Adjusting the smoothing factor by [deltaTimeMs] relative to [REFERENCE_FRAME_MS] keeps
     * bar movement speed consistent across 60 Hz, 90 Hz, and 120 Hz displays.
     */
    fun smoothBarLevel(previous: Float, target: Float, isRunning: Boolean, deltaTimeMs: Long): Float {
        if (!isRunning) return target
        if (deltaTimeMs <= 0L) return previous
        val baseFactor = if (target >= previous) ATTACK_SMOOTHING else DECAY_SMOOTHING
        val normalizedFrames = (deltaTimeMs / REFERENCE_FRAME_MS).coerceAtMost(MAX_FRAME_MULTIPLIER)
        val factor = 1f - (1f - baseFactor).pow(normalizedFrames)
        return previous + (target - previous) * factor
    }

    /**
     * Updates [smoothedOutput] in-place with time-normalized (`0f..1f`) heights for each channel
     * in [fftChannels], keeping the 16-bar frequency visualization chart smooth and consistent
     * across 60 Hz and 120 Hz displays.
     */
    fun smoothFftChannels(
        fftChannels: ByteArray,
        deltaTimeMs: Long,
        smoothedOutput: FloatArray = FloatArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT),
    ): FloatArray {
        for (i in smoothedOutput.indices) {
            val target = if (i < fftChannels.size) {
                ((fftChannels[i].toInt() and BYTE_MASK) / CHANNEL_MAX_VALUE).coerceIn(0f, 1f)
            } else {
                0f
            }
            smoothedOutput[i] = smoothBarLevel(
                previous = smoothedOutput[i],
                target = target,
                isRunning = true,
                deltaTimeMs = deltaTimeMs,
            )
        }
        return smoothedOutput
    }

    private fun computeBandPeakChannelValue(fftChannels: ByteArray, startChannel: Int, endChannelInclusive: Int): Int {
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
