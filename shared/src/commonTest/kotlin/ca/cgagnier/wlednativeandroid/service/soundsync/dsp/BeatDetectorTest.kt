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
        var peakCount = 0
        repeat(200) { i ->
            val sample = 0.3f + 0.01f * kotlin.math.sin(i * 0.1).toFloat()
            if (detector.detect(sample)) peakCount++
        }
        assertEquals(0, peakCount, "Steady signal should not trigger peaks after warmup")
    }

    @Test
    fun detect_suddenAmplitudeIncreaseTriggersPeak() {
        val detector = BeatDetector(threshold = 1.5f)
        repeat(100) { detector.detect(0.1f) }
        assertTrue(detector.detect(0.8f), "Sudden amplitude increase should trigger a peak")
    }

    @Test
    fun detect_peakDecaysOverTime() {
        val detector = BeatDetector(threshold = 1.5f, decayRate = 0.9f)
        repeat(100) { detector.detect(0.1f) }
        assertTrue(detector.detect(0.8f))
        assertFalse(detector.detect(0.8f), "Peak should decay; same level should not re-trigger immediately")
    }

    @Test
    fun detect_returnsFalseForEmptyHistory() {
        val detector = BeatDetector()
        assertFalse(detector.detect(0.5f))
    }

    @Test
    fun detect_veryLoudSignalAlwaysTriggersPeak() {
        val detector = BeatDetector(threshold = 2.0f)
        repeat(50) { detector.detect(0f) }
        assertTrue(detector.detect(1.0f), "Max amplitude should always trigger a peak")
    }

    @Test
    fun reset_clearsHistory() {
        val detector = BeatDetector()
        repeat(100) { detector.detect(0.5f) }
        detector.reset()
        assertFalse(detector.detect(0.5f))
    }

    @Test
    fun detect_withCustomThreshold_respectsThreshold() {
        listOf(
            0.6f to false,
            1.2f to true,
        ).forEach { (amplitude, expectedPeak) ->
            val detector = BeatDetector(threshold = 3.0f)
            repeat(100) { detector.detect(0.3f) }
            assertEquals(
                expectedPeak,
                detector.detect(amplitude),
                "Amplitude $amplitude with threshold 3.0 after 0.3f warmup",
            )
        }
    }
}
