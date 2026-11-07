package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import kotlin.math.abs

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
    private val smoothedFftChannels = FloatArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT)
    private var sampleBufferIndex = 0
    private var sampleBufferFilled = false
    private var pendingPeak = 0f
    private var smoothedSample = 0f
    private var hasSmoothedSample = false
    private var lastFrameTimeMs = UNSET_TIME_MS

    val sampleRate: Int get() = currentSampleRate
    val gain: Float get() = gainControl.manualGain
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
        accumulateSamples(gained)

        if (lastFrameTimeMs != UNSET_TIME_MS &&
            nowMs - lastFrameTimeMs < UdpSoundSyncPacket.PACKET_INTERVAL_MS
        ) {
            return null
        }
        lastFrameTimeMs = nowMs

        val peak = pendingPeak
        pendingPeak = 0f

        val gatedPeak = if (peak < NOISE_FLOOR_AMPLITUDE) 0f else peak
        val sampleRaw = (gatedPeak * SAMPLE_MAX_VALUE).coerceIn(0f, SAMPLE_MAX_VALUE)
        val sampleSmth = updateSmoothedSample(sampleRaw)
        val samplePeak = beatDetector.detect(peak)

        populateFftWindow()
        val fftResult = fftAnalyzer.analyze(fftWindow)
        val smoothedChannels = smoothFftChannels(fftResult.fftChannels)

        return UdpSoundSyncPacket(
            sampleRaw = sampleRaw,
            sampleSmth = sampleSmth,
            samplePeak = samplePeak,
            fftResult = smoothedChannels,
            fftMagnitude = fftResult.magnitude,
            fftMajorPeak = fftResult.majorPeak,
        )
    }

    /**
     * Updates the exponentially smoothed sample envelope (`sampleSmth`) to match
     * WLED's `sampleAvg` / `sampleAgc` behavior in `audio_reactive.cpp`.
     */
    private fun updateSmoothedSample(sampleRaw: Float): Float {
        smoothedSample = when {
            !hasSmoothedSample -> {
                hasSmoothedSample = true
                sampleRaw
            }

            sampleRaw > smoothedSample -> {
                smoothedSample * SAMPLE_ATTACK_RETENTION + sampleRaw * (1f - SAMPLE_ATTACK_RETENTION)
            }

            else -> {
                smoothedSample * SAMPLE_DECAY_RETENTION + sampleRaw * (1f - SAMPLE_DECAY_RETENTION)
            }
        }
        if (smoothedSample < MIN_SMOOTHED_SAMPLE) {
            smoothedSample = 0f
        }
        return smoothedSample.coerceIn(0f, SAMPLE_MAX_VALUE)
    }

    /**
     * Applies WLED's asymmetric rise/fall smoothing (`fftAvg` in `postProcessFFTResults`)
     * so the 16 GEQ channels respond quickly to beats and decay smoothly.
     */
    private fun smoothFftChannels(rawChannels: ByteArray): ByteArray {
        val smoothed = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT)
        for (i in 0 until UdpSoundSyncPacket.FFT_CHANNEL_COUNT) {
            val current = (rawChannels[i].toInt() and BYTE_MASK).toFloat()
            val previous = smoothedFftChannels[i]
            val next = if (current > previous) {
                current * FFT_RISE_WEIGHT + previous * (1f - FFT_RISE_WEIGHT)
            } else {
                current * FFT_FALL_WEIGHT + previous * (1f - FFT_FALL_WEIGHT)
            }
            smoothedFftChannels[i] = if (next < MIN_FFT_CHANNEL_VALUE) 0f else next
            smoothed[i] = smoothedFftChannels[i].toInt().coerceIn(0, MAX_FFT_CHANNEL_INT).toByte()
        }
        return smoothed
    }

    private fun accumulateSamples(samples: FloatArray) {
        for (sample in samples) {
            val amplitude = abs(sample)
            if (amplitude > pendingPeak) pendingPeak = amplitude
            sampleBuffer[sampleBufferIndex] = sample
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
        smoothedFftChannels.fill(0f)
        sampleBufferIndex = 0
        sampleBufferFilled = false
        pendingPeak = 0f
        smoothedSample = 0f
        hasSmoothedSample = false
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

        /** Minimum peak amplitude required before opening the sample noise gate (~1.2 / 255). */
        private const val NOISE_FLOOR_AMPLITUDE = 0.005f

        /** Envelope retention weight when signal amplitude is rising (fast attack). */
        private const val SAMPLE_ATTACK_RETENTION = 0.5f

        /** Envelope retention weight when signal amplitude is falling (smooth release). */
        private const val SAMPLE_DECAY_RETENTION = 0.85f

        /** Noise gate threshold below which `sampleSmth` snaps to 0 (matches WLED `sampleAvg < 1`). */
        private const val MIN_SMOOTHED_SAMPLE = 1.0f

        /** WLED fast-rise weight for GEQ channels (`0.75f` in `postProcessFFTResults`). */
        private const val FFT_RISE_WEIGHT = 0.75f

        /** WLED decay weight for GEQ channels (`0.22f` in `postProcessFFTResults`). */
        private const val FFT_FALL_WEIGHT = 0.22f

        /** Minimum smoothed FFT channel level before snapping to zero. */
        private const val MIN_FFT_CHANNEL_VALUE = 1.0f

        /** Maximum byte channel value as Int. */
        private const val MAX_FFT_CHANNEL_INT = 255

        /** Bitmask for unsigned byte conversion. */
        private const val BYTE_MASK = 0xFF
    }
}
