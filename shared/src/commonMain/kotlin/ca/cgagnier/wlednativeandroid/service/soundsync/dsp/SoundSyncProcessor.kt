package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import kotlin.math.abs

/**
 * Shared KMP audio-reactive DSP coordinator.
 *
 * Processes incoming normalized PCM float chunks (`-1.0..1.0`) through:
 * 1. [LowCutFilter] (stateful 2nd-order high-pass filter)
 * 2. [GainControl] (manual gain + optional AGC)
 * 3. A chronological 512-sample ring buffer
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
    private val gainControl = GainControl(manualGain = manualGain, agcEnabled = agcEnabled)
    private var fftAnalyzer = FftAnalyzer(fftSize = FFT_WINDOW_SIZE, sampleRate = currentSampleRate)
    private val beatDetector = BeatDetector()

    private val sampleBuffer = FloatArray(FFT_WINDOW_SIZE)
    private val fftWindow = FloatArray(FFT_WINDOW_SIZE)
    private var sampleBufferIndex = 0
    private var sampleBufferFilled = false
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

        var peak = 0f
        var sum = 0f
        for (sample in gained) {
            val amplitude = abs(sample)
            if (amplitude > peak) peak = amplitude
            sum += amplitude
        }
        val average = sum / gained.size
        val sampleRaw = (peak * SAMPLE_MAX_VALUE).coerceIn(0f, SAMPLE_MAX_VALUE)
        val sampleSmth = (average * SAMPLE_MAX_VALUE).coerceIn(0f, SAMPLE_MAX_VALUE)
        val samplePeak = beatDetector.detect(peak)

        populateFftWindow()
        val fftResult = fftAnalyzer.analyze(fftWindow)

        return UdpSoundSyncPacket(
            sampleRaw = sampleRaw,
            sampleSmth = sampleSmth,
            samplePeak = samplePeak,
            fftResult = fftResult.fftChannels,
            fftMagnitude = fftResult.magnitude,
            fftMajorPeak = fftResult.majorPeak,
        )
    }

    private fun accumulateSamples(samples: FloatArray) {
        for (sample in samples) {
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
        sampleBufferIndex = 0
        sampleBufferFilled = false
    }

    companion object {
        const val FFT_WINDOW_SIZE = FftAnalyzer.DEFAULT_FFT_SIZE
        const val DEFAULT_SAMPLE_RATE = FftAnalyzer.DEFAULT_SAMPLE_RATE
        const val DEFAULT_GAIN = 1.0f
        const val SAMPLE_MAX_VALUE = 255f
        private const val MIN_SAMPLE_RATE = 8000
        private const val UNSET_TIME_MS = Long.MIN_VALUE
    }
}
