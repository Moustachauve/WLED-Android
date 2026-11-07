package ca.cgagnier.wlednativeandroid.ui.soundsync

import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals

class SoundSyncUiStateTest {

    @ParameterizedTest
    @CsvSource(
        "true, NORMAL, 5.5",
        "true, VIVID, 5.5",
        "true, LAZY, 5.5",
        "true, OFF, 2.0",
        "false, NORMAL, 2.0",
        "false, OFF, 2.0",
    )
    fun displayedGain_returnsEffectiveGainOnlyWhenRunningWithAgcEnabled(
        isRunning: Boolean,
        agcMode: AgcMode,
        expectedDisplayedGain: Float,
    ) {
        val uiState = SoundSyncUiState(
            isRunning = isRunning,
            gain = 2.0f,
            effectiveGain = 5.5f,
            agcMode = agcMode,
        )

        assertEquals(expectedDisplayedGain, uiState.displayedGain, 0.0001f)
    }
}
