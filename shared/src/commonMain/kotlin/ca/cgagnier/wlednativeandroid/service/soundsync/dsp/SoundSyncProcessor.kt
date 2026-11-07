package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import kotlin.math.abs
import kotlin.math.pow

/**
 * Shared KMP audio-reactive DSP coordinator.
 *
 * Processes incoming normalized PCM float chunks (`-1.0..1.0`) through:
 * 1. [LowCutFilter] (stateful 2nd-order high-pass filter)
 * 2. [GainControl] (manual gain + optional AGC)
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
    agcEnabled: Boolean = false,
) {
    constructor() : this(DEFAULT_SAMPLE_RATE, DEFAULT_GAIN, false)

    private var currentSampleRate = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
    private var lowCutFilter = LowCutFilter(sampleRate = currentSampleRate)
    private val gainControl = GainControl(
        manualGain = manualGain,
        agcEnabled = agcEnabled,
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
    private var lastFrameTimeMs = UNSET_TIME_MS

    val sampleRate: Int get() = currentSampleRate
    val gain: Float get() = gainControl.manualGain
    val effectiveGain: Float get() = gainControl.effectiveGain
    val agcEnabled: Boolean get() = gainControl.agcEnabled

    /**
     * Updates the audio sample rate if the hardware capture rate differs from the current rate.
     */
    fun configureSampleRate(sampleRate: Int) {
        val validRate = sampleRate.coerceAtLeast(MIN_SAMPLE_RATE)
        if (validRate == currentSampleRate) return
        currentSampleRate = validRate
        lowCutFilter = LowCutFilter(sampleRate = validRate)
        fftAnalyzer = FftAnalyzer(fftSize = FFT_WINDOW_SIZE, sampleRate = validRate)
        resetSampleBuffer()
    }

    fun setGain(gain: Float) {
        gainControl.setManualGain(gain)
    }

    fun setAgcEnabled(enabled: Boolean) {
        gainControl.setAgcEnabled(enabled)
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
        lastFrameTimeMs = UNSET_TIME_MS
    }

    /**
     * Processes a chunk of normalized PCM audio samples (`-1.0..1.0`).
     *
     * @param samples Input PCM samples.
     * @param nowMs Current monotonic/wall-clock timestamp in milliseconds used for 50 fps throttling.
     * @return A [UdpSoundSyncPacket] if at least [UdpSoundSyncPacket.PACKET_INTERVAL_MS] has elapsed
     *   since the last emitted frame, or `null` if throttled or [samples] is empty.
     */
    fun process(samples: FloatArray, nowMs: Long): UdpSoundSyncPacket? {
        if (samples.isEmpty()) return null

        // Always run stateful filters and accumulate into the FFT ring buffer
        val filtered = lowCutFilter.process(samples)
        val gained = gainControl.process(filtered)
        accumulateSamples(filtered, gained)

        if (lastFrameTimeMs != UNSET_TIME_MS &&
            nowMs - lastFrameTimeMs < UdpSoundSyncPacket.PACKET_INTERVAL_MS
        ) {
            return null
        }
        val deltaTimeMs = if (lastFrameTimeMs == UNSET_TIME_MS) {
            UdpSoundSyncPacket.PACKET_INTERVAL_MS
        } else {
            (nowMs - lastFrameTimeMs).coerceAtLeast(1L)
        }
        lastFrameTimeMs = nowMs

        val rawPeak = pendingRawPeak
        val gainedPeak = pendingGainedPeak
        pendingRawPeak = 0f
        pendingGainedPeak = 0f

        // Check noise gate (squelch) against pre-gain peak amplitude so AGC or high manual gain
        // does not amplify ambient room noise past the gate in a silent room.
        val gatedPeak = if (rawPeak < NOISE_FLOOR_AMPLITUDE) 0f else gainedPeak
        val sampleRaw = (gatedPeak * SAMPLE_MAX_VALUE).coerceIn(0f, SAMPLE_MAX_VALUE)
        val sampleSmth = updateSmoothedSample(sampleRaw, deltaTimeMs)
        val samplePeak = beatDetector.detect(gatedPeak)

        populateFftWindow()
        val isSilent = sampleSmth < MIN_SMOOTHED_SAMPLE
        val fftResult = fftAnalyzer.analyze(fftWindow, isSilent = isSilent, deltaTimeMs = deltaTimeMs)

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
    private fun updateSmoothedSample(sampleRaw: Float, deltaTimeMs: Long): Float {
        val normalizedFrames = (deltaTimeMs / REFERENCE_PACKET_INTERVAL_MS)
            .coerceIn(MIN_FRAME_MULTIPLIER, MAX_FRAME_MULTIPLIER)
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
        const val SAMPLE_MAX_VALUE = 255f
        private const val MIN_SAMPLE_RATE = 8000
        private const val UNSET_TIME_MS = Long.MIN_VALUE

        /**
         * Baseline microphone sensitivity multiplier applied in manual gain mode so the
         * `1.0x..10.0x` UI slider spans `3x..30x` effective gain (matching AGC's `32x` range).
         */
        private const val MANUAL_GAIN_SENSITIVITY = 3.0f

        /** Minimum pre-gain peak amplitude required before opening the sample noise gate (squelch). */
        private const val NOISE_FLOOR_AMPLITUDE = 0.005f

        /** Envelope retention weight when signal amplitude is rising (fast attack). */
        private const val SAMPLE_ATTACK_RETENTION = 0.5f

        /** Envelope retention weight when signal amplitude is falling (smooth release). */
        private const val SAMPLE_DECAY_RETENTION = 0.85f

        /** Reference 20 ms packet interval for normalizing envelope smoothing weights. */
        private const val REFERENCE_PACKET_INTERVAL_MS = 20f
        private const val MIN_FRAME_MULTIPLIER = 0.25f
        private const val MAX_FRAME_MULTIPLIER = 6f

        /** Noise gate threshold below which `sampleSmth` snaps to 0 (matches WLED `volumeSmth < 1`). */
        private const val MIN_SMOOTHED_SAMPLE = 1.0f
    }
}
