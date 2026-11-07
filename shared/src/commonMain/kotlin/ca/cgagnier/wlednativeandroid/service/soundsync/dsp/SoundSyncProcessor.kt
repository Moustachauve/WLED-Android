package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import kotlin.math.abs
import kotlin.math.pow

/**
 * Shared KMP audio-reactive DSP coordinator.
 *
 * Processes incoming normalized PCM float chunks (`-1.0..1.0`) through:
 * 1. [LowCutFilter] (stateful 2nd-order high-pass filter)
 * 2. [GainControl] (manual gain + optional two-zone AGC)
 * 3. A chronological 1024-sample ring buffer
 * 4. [BeatDetector] and [FftAnalyzer] (throttled to [UdpSoundSyncPacket.PACKET_INTERVAL_MS])
 *
 * Every incoming audio chunk is filtered, gain-adjusted, and accumulated into the
 * ring buffer so stateful filter continuity and the FFT window are preserved even
 * when packet emission is throttled.
 */
class SoundSyncProcessor(
    sampleRate: Int = DEFAULT_SAMPLE_RATE,
    manualGain: Float = DEFAULT_GAIN,
    agcMode: AgcMode = AgcMode.OFF,
    squelch: Int = DEFAULT_SQUELCH,
) {
    constructor() : this(DEFAULT_SAMPLE_RATE, DEFAULT_GAIN, AgcMode.OFF, DEFAULT_SQUELCH)

    private var currentSampleRate = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
    var squelch: Int = squelch.coerceIn(MIN_SQUELCH, MAX_SQUELCH)
        private set
    private var currentSquelchAmplitude: Float = squelchToAmplitude(this.squelch)

    private var lowCutFilter = LowCutFilter(sampleRate = currentSampleRate)
    private val gainControl = GainControl(
        manualGain = manualGain,
        agcMode = agcMode,
        sampleRate = currentSampleRate,
        squelchAmplitude = currentSquelchAmplitude,
        manualGainMultiplier = MANUAL_GAIN_SENSITIVITY,
    )
    private var fftAnalyzer = FftAnalyzer(fftSize = FFT_WINDOW_SIZE, sampleRate = currentSampleRate)
    private val beatDetector = BeatDetector()

    private val sampleBuffer = FloatArray(FFT_WINDOW_SIZE)
    private val fftWindow = FloatArray(FFT_WINDOW_SIZE)
    private var sampleBufferIndex = 0
    private var sampleBufferFilled = false
    private var pendingRawPeak = 0f
    private var pendingGainedPeak = 0f
    private var smoothedSample = 0f
    private var smoothedEffectiveGain = DEFAULT_GAIN
    private var lastFrameTimeMs = UNSET_TIME_MS
    private var nextDeadlineMs = UNSET_TIME_MS

    val sampleRate: Int get() = currentSampleRate
    val gain: Float get() = gainControl.manualGain
    val effectiveGain: Float
        get() = if (gainControl.agcEnabled) smoothedEffectiveGain else gainControl.manualGain
    val agcMode: AgcMode get() = gainControl.agcMode
    val agcEnabled: Boolean get() = gainControl.agcEnabled

    /**
     * Updates the audio sample rate if the hardware capture rate differs from the current rate.
     */
    fun configureSampleRate(sampleRate: Int) {
        val validRate = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
        if (validRate == currentSampleRate) return
        currentSampleRate = validRate
        lowCutFilter = LowCutFilter(sampleRate = validRate)
        gainControl.setSampleRate(validRate)
        fftAnalyzer = FftAnalyzer(fftSize = FFT_WINDOW_SIZE, sampleRate = validRate)
        resetSampleBuffer()
    }

    fun setGain(gain: Float) {
        gainControl.setManualGain(gain)
    }

    fun setAgcMode(mode: AgcMode) {
        gainControl.setAgcMode(mode)
        if (!gainControl.agcEnabled) {
            smoothedEffectiveGain = DEFAULT_GAIN
        }
    }

    fun setSquelch(squelch: Int) {
        val clamped = squelch.coerceIn(MIN_SQUELCH, MAX_SQUELCH)
        this.squelch = clamped
        currentSquelchAmplitude = squelchToAmplitude(clamped)
        gainControl.setSquelchAmplitude(currentSquelchAmplitude)
    }

    /**
     * Resets all filter, AGC, beat detector, and ring-buffer state.
     */
    fun reset() {
        lowCutFilter.reset()
        gainControl.reset()
        beatDetector.reset()
        fftAnalyzer.reset()
        resetSampleBuffer()
        smoothedEffectiveGain = DEFAULT_GAIN
        lastFrameTimeMs = UNSET_TIME_MS
        nextDeadlineMs = UNSET_TIME_MS
    }

    /**
     * Processes a chunk of normalized PCM audio samples (`-1.0..1.0`).
     *
     * @param samples Input PCM samples.
     * @param nowMs Current monotonic/sample-clock timestamp in milliseconds used for 50 Hz scheduling.
     * @return A [UdpSoundSyncPacket] if the next [UdpSoundSyncPacket.PACKET_INTERVAL_MS] deadline
     *   has been reached, or `null` if throttled or [samples] is empty.
     */
    fun process(samples: FloatArray, nowMs: Long): UdpSoundSyncPacket? {
        if (samples.isEmpty()) return null

        // Always run stateful filters and accumulate into the FFT ring buffer
        val filtered = lowCutFilter.process(samples)
        val gained = gainControl.process(samples = filtered)
        accumulateSamples(filtered, gained)

        if (nextDeadlineMs != UNSET_TIME_MS && nowMs < nextDeadlineMs) {
            return null
        }
        val deltaTimeMs = if (lastFrameTimeMs == UNSET_TIME_MS) {
            UdpSoundSyncPacket.PACKET_INTERVAL_MS
        } else {
            nowMs - lastFrameTimeMs
        }
        lastFrameTimeMs = nowMs
        nextDeadlineMs = if (nextDeadlineMs == UNSET_TIME_MS) {
            nowMs + UdpSoundSyncPacket.PACKET_INTERVAL_MS
        } else {
            maxOf(
                nextDeadlineMs + UdpSoundSyncPacket.PACKET_INTERVAL_MS,
                nowMs - UdpSoundSyncPacket.PACKET_INTERVAL_MS + 1L,
            )
        }
        val normalizedFrames = normalizePacketFrameDelta(deltaTimeMs)

        val rawPeak = pendingRawPeak
        val gainedPeak = pendingGainedPeak
        pendingRawPeak = 0f
        pendingGainedPeak = 0f

        // Check noise gate (squelch) against pre-gain peak amplitude so AGC or high manual gain
        // does not amplify ambient room noise past the gate in a silent room.
        val gatedPeak = if (rawPeak < currentSquelchAmplitude) 0f else gainedPeak
        val sampleRaw = (gatedPeak * SAMPLE_MAX_VALUE).coerceIn(0f, SAMPLE_MAX_VALUE)
        val sampleSmth = updateSmoothedSample(sampleRaw, normalizedFrames)
        updateSmoothedEffectiveGain(normalizedFrames)
        val samplePeak = beatDetector.detect(gatedPeak)

        populateFftWindow()
        val isSilent = sampleSmth < MIN_SMOOTHED_SAMPLE
        val fftResult = fftAnalyzer.analyze(fftWindow, isSilent = isSilent, normalizedFrames = normalizedFrames)

        return UdpSoundSyncPacket(
            sampleRaw = sampleRaw,
            sampleSmth = sampleSmth,
            samplePeak = samplePeak,
            fftResult = fftResult.fftChannels,
            fftMagnitude = fftResult.magnitude,
            fftMajorPeak = fftResult.majorPeak,
        )
    }

    /**
     * Updates the exponentially smoothed sample envelope (`sampleSmth`) using a fast
     * attack (`0.50`) and intentionally slower release (`0.85`) for smoother LED fades
     * between 20 ms UDP frames, snapping to zero below [MIN_SMOOTHED_SAMPLE].
     */
    private fun updateSmoothedSample(sampleRaw: Float, normalizedFrames: Float): Float {
        val baseRetention = if (sampleRaw > smoothedSample) {
            SAMPLE_ATTACK_RETENTION
        } else {
            SAMPLE_DECAY_RETENTION
        }
        val retention = baseRetention.pow(normalizedFrames)
        smoothedSample = smoothedSample * retention + sampleRaw * (1f - retention)
        if (smoothedSample < MIN_SMOOTHED_SAMPLE) {
            smoothedSample = 0f
        }
        return smoothedSample.coerceIn(0f, SAMPLE_MAX_VALUE)
    }

    private fun updateSmoothedEffectiveGain(normalizedFrames: Float) {
        if (!gainControl.agcEnabled) return
        val retention = DISPLAY_AGC_RETENTION.pow(normalizedFrames)
        smoothedEffectiveGain = (
            smoothedEffectiveGain * retention +
                gainControl.effectiveGain * (1f - retention)
            ).coerceIn(0f, GainControl.MAX_MANUAL_GAIN)
    }

    private fun accumulateSamples(filtered: FloatArray, gained: FloatArray) {
        for (i in gained.indices) {
            val rawAmplitude = abs(filtered[i])
            if (rawAmplitude > pendingRawPeak) pendingRawPeak = rawAmplitude
            val gainedSample = gained[i]
            val gainedAmplitude = abs(gainedSample)
            if (gainedAmplitude > pendingGainedPeak) pendingGainedPeak = gainedAmplitude
            sampleBuffer[sampleBufferIndex] = gainedSample
            sampleBufferIndex = (sampleBufferIndex + 1) % FFT_WINDOW_SIZE
            if (sampleBufferIndex == 0) sampleBufferFilled = true
        }
    }

    private fun populateFftWindow() {
        if (!sampleBufferFilled && sampleBufferIndex == 0) {
            fftWindow.fill(0f)
            return
        }
        val start = if (sampleBufferFilled) sampleBufferIndex else 0
        for (i in 0 until FFT_WINDOW_SIZE) {
            fftWindow[i] = sampleBuffer[(start + i) % FFT_WINDOW_SIZE]
        }
    }

    private fun resetSampleBuffer() {
        sampleBuffer.fill(0f)
        fftWindow.fill(0f)
        sampleBufferIndex = 0
        sampleBufferFilled = false
        pendingRawPeak = 0f
        pendingGainedPeak = 0f
        smoothedSample = 0f
    }

    companion object {
        const val FFT_WINDOW_SIZE = FftAnalyzer.DEFAULT_FFT_SIZE
        const val DEFAULT_SAMPLE_RATE = FftAnalyzer.DEFAULT_SAMPLE_RATE
        const val DEFAULT_GAIN = 1.0f
        const val DEFAULT_SQUELCH = 10
        const val MIN_SQUELCH = 0
        const val MAX_SQUELCH = 100
        const val SAMPLE_MAX_VALUE = 255f
        private const val MIN_SAMPLE_RATE = 8000
        private const val UNSET_TIME_MS = Long.MIN_VALUE

        /**
         * Baseline microphone sensitivity multiplier applied in manual gain mode so the
         * `1.0x..30.0x` UI slider spans `3x..90x` effective gain (matching AGC's `90x` range).
         */
        private const val MANUAL_GAIN_SENSITIVITY = 3.0f

        /** Pre-gain amplitude step per unit of [squelch] (`10` -> `0.005f`, `100` -> `0.05f`). */
        private const val SQUELCH_AMPLITUDE_STEP = 0.0005f

        /** Envelope retention weight when signal amplitude is rising (fast attack). */
        private const val SAMPLE_ATTACK_RETENTION = 0.5f

        /** Envelope retention weight when signal amplitude is falling (smooth release). */
        private const val SAMPLE_DECAY_RETENTION = 0.85f

        /** Per-frame retention weight (~240 ms time constant, 0.92 retention per 20 ms) for UI display of AGC gain. */
        private const val DISPLAY_AGC_RETENTION = 0.92f

        /** Noise gate threshold below which `sampleSmth` snaps to 0 (matches WLED `volumeSmth < 1`). */
        private const val MIN_SMOOTHED_SAMPLE = 1.0f

        internal fun squelchToAmplitude(squelch: Int): Float {
            val clamped = squelch.coerceIn(MIN_SQUELCH, MAX_SQUELCH)
            return (clamped * SQUELCH_AMPLITUDE_STEP).coerceAtLeast(GainControl.MIN_SQUELCH_AMPLITUDE)
        }
    }
}

private const val MIN_PACKET_FRAME_MULTIPLIER = 0.25f
private const val MAX_PACKET_FRAME_MULTIPLIER = 6f

/**
 * Normalizes [deltaTimeMs] relative to [UdpSoundSyncPacket.PACKET_INTERVAL_MS] so
 * frame-to-frame exponential smoothing stays consistent regardless of capture cadence.
 */
internal fun normalizePacketFrameDelta(deltaTimeMs: Long): Float =
    (deltaTimeMs.toFloat() / UdpSoundSyncPacket.PACKET_INTERVAL_MS)
        .coerceIn(MIN_PACKET_FRAME_MULTIPLIER, MAX_PACKET_FRAME_MULTIPLIER)
