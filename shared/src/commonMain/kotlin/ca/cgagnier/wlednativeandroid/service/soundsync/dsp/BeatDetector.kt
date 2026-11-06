package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.math.abs

/**
 * Simple peak-based beat detector.
 *
 * Tracks a running average of recent sample amplitudes and triggers a "peak"
 * (beat) when the current sample significantly exceeds that average. After
 * a peak is detected, the average jumps to the current amplitude so that a
 * sustained loud signal doesn't continuously re-trigger.
 *
 * This is intentionally crude — WLED's on-device beat detection is similarly
 * simple — but sufficient to drive the `samplePeak` field in the UDP sync
 * packet for effects that pulse on beats.
 */
class BeatDetector(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val decayRate: Float = DEFAULT_DECAY_RATE,
    private val minEnergy: Float = DEFAULT_MIN_ENERGY,
) {
    private var averageEnergy = 0f
    private var initialized = false

    /**
     * Feeds a sample amplitude into the detector.
     *
     * @param sampleAmplitude The absolute amplitude of the current audio sample (0.0 - 1.0+).
     * @return `true` if a beat/peak was detected on this sample.
     */
    fun detect(sampleAmplitude: Float): Boolean {
        val amplitude = abs(sampleAmplitude)

        if (!initialized) {
            averageEnergy = amplitude
            initialized = true
            return false
        }

        // A peak is when current amplitude significantly exceeds the running average
        val isPeak = amplitude > averageEnergy * threshold && amplitude > minEnergy

        // Update average: jump to current level after a peak (prevents re-trigger),
        // otherwise track slowly with decay
        averageEnergy = if (isPeak) {
            amplitude
        } else {
            averageEnergy * decayRate + amplitude * (1f - decayRate)
        }

        return isPeak
    }

    /** Clears the detector's state. */
    fun reset() {
        averageEnergy = 0f
        initialized = false
    }

    companion object {
        const val DEFAULT_THRESHOLD = 1.5f
        const val DEFAULT_DECAY_RATE = 0.85f
        const val DEFAULT_MIN_ENERGY = 0.05f
    }
}
