package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BeatDetectorTest {

    @Test
    fun detect_silenceNeverTriggersPeak() {
        val detector = BeatDetector()
        repeat(100) {
            assertFalse(detector.detect(0f), "Silence should never trigger a peak")
        }
    }

    @Test
    fun detect_steadySignalDoesNotTriggerPeak() {
        val detector = BeatDetector(threshold = 1.5f)
        // Feed a steady signal; after warmup, no peaks should be detected
        var peakCount = 0
        repeat(200) { i ->
            // Small variation around a steady level
            val sample = 0.3f + 0.01f * kotlin.math.sin(i * 0.1).toFloat()
            if (detector.detect(sample)) peakCount++
        }
        assertEquals(0, peakCount, "Steady signal should not trigger peaks after warmup")
    }

    @Test
    fun detect_suddenAmplitudeIncreaseTriggersPeak() {
        val detector = BeatDetector(threshold = 1.5f)
        // Warm up with quiet signal
        repeat(100) { detector.detect(0.1f) }
        // Sudden loud sample should trigger a peak
        assertTrue(detector.detect(0.8f), "Sudden amplitude increase should trigger a peak")
    }

    @Test
    fun detect_peakDecaysOverTime() {
        val detector = BeatDetector(threshold = 1.5f, decayRate = 0.9f)
        // Warm up
        repeat(100) { detector.detect(0.1f) }
        // Trigger a peak
        assertTrue(detector.detect(0.8f))
        // Next sample at same level should NOT trigger (energy has decayed)
        assertFalse(detector.detect(0.8f), "Peak should decay; same level should not re-trigger immediately")
    }

    @Test
    fun detect_returnsFalseForEmptyHistory() {
        val detector = BeatDetector()
        // First sample ever — not enough history, should return false
        assertFalse(detector.detect(0.5f))
    }

    @Test
    fun detect_veryLoudSignalAlwaysTriggersPeak() {
        val detector = BeatDetector(threshold = 2.0f)
        // Warm up with silence
        repeat(50) { detector.detect(0f) }
        // Extremely loud signal should always trigger
        assertTrue(detector.detect(1.0f), "Max amplitude should always trigger a peak")
    }

    @Test
    fun detect_peakOnlyLastsOneCall() {
        val detector = BeatDetector(threshold = 1.3f, decayRate = 0.5f)
        // Warm up with quiet signal
        repeat(100) { detector.detect(0.1f) }
        // Trigger peak
        assertTrue(detector.detect(0.9f))
        // Immediately after, a moderate sample should not trigger
        assertFalse(detector.detect(0.2f))
    }

    @Test
    fun reset_clearsHistory() {
        val detector = BeatDetector()
        // Build up history
        repeat(100) { detector.detect(0.5f) }
        detector.reset()
        // After reset, first call should return false (no history)
        assertFalse(detector.detect(0.5f))
    }

    @Test
    fun detect_withCustomThreshold_respectsThreshold() {
        val detector = BeatDetector(threshold = 3.0f)
        // Warm up with moderate signal
        repeat(100) { detector.detect(0.3f) }
        // A 2x increase should NOT trigger with threshold 3.0
        assertFalse(detector.detect(0.6f), "2x increase should not trigger with threshold 3.0")
        // A 4x increase should trigger
        assertTrue(detector.detect(1.2f), "4x increase should trigger with threshold 3.0")
    }
}
