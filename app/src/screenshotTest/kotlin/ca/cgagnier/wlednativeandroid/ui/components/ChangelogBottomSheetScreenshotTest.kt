package ca.cgagnier.wlednativeandroid.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.domain.VersionChangelog
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import com.android.tools.screenshot.PreviewTest

@OptIn(ExperimentalMaterial3Api::class)
class ChangelogBottomSheetScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun ChangelogSheetLightPreview() {
        ChangelogSheetPreview(darkTheme = false)
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun ChangelogSheetDarkPreview() {
        ChangelogSheetPreview(darkTheme = true)
    }

    @Composable
    private fun ChangelogSheetPreview(darkTheme: Boolean) {
        WLEDNativeTheme(darkTheme = darkTheme, dynamicColor = false) {
            Surface(
                color = BottomSheetDefaults.ContainerColor,
                shape = BottomSheetDefaults.ExpandedShape,
            ) {
                ChangelogSheetContent(
                    changelogs = SAMPLE_CHANGELOGS,
                    onDismiss = {},
                    modifier = Modifier
                        .height(640.dp)
                        .padding(top = 16.dp),
                )
            }
        }
    }

    companion object {
        private val SAMPLE_CHANGELOGS = listOf(
            VersionChangelog(
                version = "dev",
                content = """
                    ### What's New Changelog
                    Automatically see release notes in *per-version* cards after an app update, or revisit them anytime from Settings.

                    ### To the Moon and Beyond!
                    Added over-the-air (`OTA`) firmware update support for **QuinLED** and **MoonModules** boards.
                """.trimIndent(),
                isDev = true,
            ),
            VersionChangelog(
                version = "7.0.0",
                content = """
                    ### Shiny New Widgets
                    Control lights without even opening the app! These redesigned widgets are interactive and sync in *real-time* over `WebSocket`.

                    ### Quick Control
                    Flick the switch directly from **Android Quick Settings**—like having a dedicated WLED remote in your notification shade.

                    ### Other Improvements & Fixes
                    - Added support for `wled://` deep links and app shortcuts.
                    - Added German and Chinese translations alongside auto-discovery improvements.
                """.trimIndent(),
            ),
        )
    }
}
