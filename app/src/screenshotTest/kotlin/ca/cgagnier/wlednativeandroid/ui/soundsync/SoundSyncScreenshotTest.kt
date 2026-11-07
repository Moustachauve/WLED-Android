package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import com.android.tools.screenshot.PreviewTest

class SoundSyncScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SoundSyncLightPreview() {
        SoundSyncPreview(darkTheme = false)
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun SoundSyncDarkPreview() {
        SoundSyncPreview(darkTheme = true)
    }

    @Composable
    private fun SoundSyncPreview(darkTheme: Boolean) {
        WLEDNativeTheme(darkTheme = darkTheme, dynamicColor = false) {
            SoundSyncContent(
                uiState = SoundSyncUiState(
                    isRunning = false,
                    hasRecordAudioPermission = true,
                    gain = 1.0f,
                    effectiveGain = 1.0f,
                    agcEnabled = true,
                    agcMode = AgcMode.NORMAL,
                    squelch = 10,
                ),
            )
        }
    }
}
