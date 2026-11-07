package ca.cgagnier.wlednativeandroid.ui.soundsync

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class SoundSyncUiStateTest {

    @ParameterizedTest
    @CsvSource(
        "true, true, 5.5",
        "true, false, 2.0",
        "false, true, 2.0",
        "false, false, 2.0",
    )
    fun displayedGain_returnsEffectiveGainOnlyWhenRunningWithAgcEnabled(
        isRunning: Boolean,
        agcEnabled: Boolean,
        expectedDisplayedGain: Float,
    ) {
        val uiState = SoundSyncUiState(
            isRunning = isRunning,
            gain = 2.0f,
            effectiveGain = 5.5f,
            agcEnabled = agcEnabled,
        )

        assertEquals(expectedDisplayedGain, uiState.displayedGain, 0.0001f)
    }
}
