package ca.cgagnier.wlednativeandroid.ui.soundsync

import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class SoundSyncUiStateTest {

    @ParameterizedTest
    @CsvSource(
        "true, true, NORMAL, 5.5",
        "true, true, VIVID, 5.5",
        "true, true, LAZY, 5.5",
        "true, false, NORMAL, 2.0",
        "false, true, NORMAL, 2.0",
        "false, false, NORMAL, 2.0",
    )
    fun displayedGain_returnsEffectiveGainOnlyWhenRunningWithAgcEnabled(
        isRunning: Boolean,
        agcEnabled: Boolean,
        agcMode: AgcMode,
        expectedDisplayedGain: Float,
    ) {
        val uiState = SoundSyncUiState(
            isRunning = isRunning,
            gain = 2.0f,
            effectiveGain = 5.5f,
            agcEnabled = agcEnabled,
            agcMode = agcMode,
        )

        assertEquals(expectedDisplayedGain, uiState.displayedGain, 0.0001f)
    }
}
