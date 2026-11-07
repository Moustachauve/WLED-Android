package ca.cgagnier.wlednativeandroid.service.soundsync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoundSyncIconVisualizerTest {

    @Test
    fun computeBarLevelsReturnsRestingLevelsWhenNotRunning() {
        val fftChannels = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT) { 255.toByte() }

        val levels = SoundSyncIconVisualizer.computeBarLevels(
            isRunning = false,
            fftChannels = fftChannels,
            elapsedTimeMs = 500L,
        )

        for (i in 0 until SoundSyncIconVisualizer.BAR_COUNT) {
            assertEquals(SoundSyncIconVisualizer.restingBarLevel(i), levels[i], 0.0001f)
        }
    }

    @Test
    fun computeBarLevelsProducesVaryingIdleWaveInSilenceWhenRunning() {
        val silentChannels = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT)

        val levelsAtStart = SoundSyncIconVisualizer.computeBarLevels(
            isRunning = true,
            fftChannels = silentChannels,
            elapsedTimeMs = 0L,
        )
        val levelsLater = SoundSyncIconVisualizer.computeBarLevels(
            isRunning = true,
            fftChannels = silentChannels,
            elapsedTimeMs = 300L,
        )

        assertTrue(
            levelsAtStart.all {
                it in SoundSyncIconVisualizer.MIN_BAR_LEVEL..SoundSyncIconVisualizer.IDLE_MAX_BAR_LEVEL
            },
        )
        assertFalse(levelsAtStart.contentEquals(levelsLater))
    }

    @Test
    fun computeBarLevelsScalesCorrespondingBarToMaxWhenFftBandIsAtPeak() {
        val fftChannels = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT).apply {
            // Channel 7 maps to the center bar (index 2: channels 6..9)
            this[7] = 255.toByte()
        }

        val levels = SoundSyncIconVisualizer.computeBarLevels(
            isRunning = true,
            fftChannels = fftChannels,
            elapsedTimeMs = 0L,
        )

        assertEquals(SoundSyncIconVisualizer.MAX_BAR_LEVEL, levels[2], 0.0001f)
        assertTrue(levels[0] <= SoundSyncIconVisualizer.IDLE_MAX_BAR_LEVEL)
        assertTrue(levels[4] <= SoundSyncIconVisualizer.IDLE_MAX_BAR_LEVEL)
    }

    @Test
    fun smoothBarLevelAppliesFasterAttackThanDecayWhenRunning() {
        val attackResult = SoundSyncIconVisualizer.smoothBarLevel(
            previous = 0.2f,
            target = 1.0f,
            isRunning = true,
        )
        val decayResult = SoundSyncIconVisualizer.smoothBarLevel(
            previous = 1.0f,
            target = 0.2f,
            isRunning = true,
        )

        val attackDelta = attackResult - 0.2f
        val decayDelta = 1.0f - decayResult
        assertTrue(attackDelta > decayDelta)
    }
}
