package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import com.android.tools.screenshot.PreviewTest

class SettingsScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun SettingsLightPreview() {
        SettingsPreview(darkTheme = false)
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun SettingsDarkPreview() {
        SettingsPreview(darkTheme = true)
    }

    /** Wide windows (tablets, foldables) lay the settings cards out in two columns. */
    @PreviewTest
    @Preview(showBackground = true, widthDp = 900, heightDp = 480)
    @Composable
    fun SettingsTwoColumnPreview() {
        SettingsPreview(darkTheme = false)
    }

    @Composable
    private fun SettingsPreview(darkTheme: Boolean) {
        WLEDNativeTheme(darkTheme = darkTheme, dynamicColor = false) {
            SettingsContent(
                settingsState = SettingsState(
                    isAutoDiscoveryEnabled = true,
                    showOfflineLast = true,
                    showHiddenDevices = false,
                    theme = ThemeSettings.Auto,
                ),
                navigateUp = {},
            )
        }
    }
}
