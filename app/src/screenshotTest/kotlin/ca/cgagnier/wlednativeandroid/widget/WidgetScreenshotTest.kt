package ca.cgagnier.wlednativeandroid.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.widget.components.WidgetScreenshotCard
import com.android.tools.screenshot.PreviewTest

@Suppress("MagicNumber")
class WidgetScreenshotTest {

    @PreviewTest
    @Preview(widthDp = 240, heightDp = 110, showBackground = true)
    @Composable
    fun WidgetWideOnLightPreview() {
        WidgetScreenshotCard(
            data = createWideOnData(),
            isDark = false,
        )
    }

    @PreviewTest
    @Preview(widthDp = 240, heightDp = 110, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun WidgetWideOnDarkPreview() {
        WidgetScreenshotCard(
            data = createWideOnData(),
            isDark = true,
        )
    }

    @PreviewTest
    @Preview(widthDp = 150, heightDp = 110, showBackground = true)
    @Composable
    fun WidgetNarrowOffLightPreview() {
        WidgetScreenshotCard(
            data = createNarrowOffData(),
            isDark = false,
            isNarrow = true,
        )
    }

    @PreviewTest
    @Preview(widthDp = 150, heightDp = 110, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun WidgetNarrowOffDarkPreview() {
        WidgetScreenshotCard(
            data = createNarrowOffData(),
            isDark = true,
            isNarrow = true,
        )
    }

    @PreviewTest
    @Preview(widthDp = 240, heightDp = 110, showBackground = true)
    @Composable
    fun WidgetWideOfflineLightPreview() {
        WidgetScreenshotCard(
            data = createOfflineData(),
            isDark = false,
        )
    }

    @PreviewTest
    @Preview(widthDp = 240, heightDp = 110, showBackground = true)
    @Composable
    fun WidgetWideQuickActionsLightPreview() {
        WidgetScreenshotCard(
            data = createQuickActionsData(),
            isDark = false,
        )
    }

    private fun createWideOnData() = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:01",
        address = "192.168.1.101",
        name = "WLED Device",
        isOn = true,
        isOnline = true,
        color = 0xFF00BFFF.toInt(),
        lastUpdated = 1_700_000_000_000L,
    )

    private fun createNarrowOffData() = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:02",
        address = "192.168.1.102",
        name = "Small widget with a long name",
        isOn = false,
        isOnline = true,
        color = 0xFF0000FF.toInt(),
        lastUpdated = 1_700_000_000_000L,
    )

    private fun createOfflineData() = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:03",
        address = "192.168.1.103",
        name = "Offline device",
        isOn = false,
        isOnline = false,
        color = 0xFFFF8000.toInt(),
        lastUpdated = 1_700_000_000_000L,
    )

    private fun createQuickActionsData() = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:04",
        address = "192.168.1.104",
        name = "WLED Device",
        isOn = true,
        isOnline = true,
        color = 0xFFDFFF00.toInt(),
        lastUpdated = 1_700_000_000_000L,
        quickActionsEnabled = true,
    )
}
