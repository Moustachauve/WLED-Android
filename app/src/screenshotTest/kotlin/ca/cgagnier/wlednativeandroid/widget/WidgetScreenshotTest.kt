package ca.cgagnier.wlednativeandroid.widget

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.widget.components.WidgetScreenshotCard
import com.android.tools.screenshot.PreviewTest

private const val WIDE_WIDTH_DP = 240
private const val NARROW_WIDTH_DP = 150
private const val WIDGET_HEIGHT_DP = 110
private const val DEEP_SKY_BLUE_ARGB = 0xFF00BFFF.toInt()
private const val BLUE_ARGB = 0xFF0000FF.toInt()
private const val ORANGE_ARGB = 0xFFFF8000.toInt()
private const val CHARTREUSE_ARGB = 0xFFDFFF00.toInt()
private const val FIXED_TIMESTAMP = 1_700_000_000_000L

class WidgetScreenshotTest {

    @PreviewTest
    @Preview(widthDp = WIDE_WIDTH_DP, heightDp = WIDGET_HEIGHT_DP, showBackground = true)
    @Composable
    fun WidgetWideOnLightPreview() {
        WidgetScreenshotCard(
            data = widgetData(),
            isDark = false,
        )
    }

    @PreviewTest
    @Preview(
        widthDp = WIDE_WIDTH_DP,
        heightDp = WIDGET_HEIGHT_DP,
        showBackground = true,
        uiMode = Configuration.UI_MODE_NIGHT_YES,
    )
    @Composable
    fun WidgetWideOnDarkPreview() {
        WidgetScreenshotCard(
            data = widgetData(),
            isDark = true,
        )
    }

    @PreviewTest
    @Preview(widthDp = NARROW_WIDTH_DP, heightDp = WIDGET_HEIGHT_DP, showBackground = true)
    @Composable
    fun WidgetNarrowOffLightPreview() {
        WidgetScreenshotCard(
            data = widgetData(name = "Small widget with a long name", isOn = false, color = BLUE_ARGB),
            isDark = false,
            isNarrow = true,
        )
    }

    @PreviewTest
    @Preview(
        widthDp = NARROW_WIDTH_DP,
        heightDp = WIDGET_HEIGHT_DP,
        showBackground = true,
        uiMode = Configuration.UI_MODE_NIGHT_YES,
    )
    @Composable
    fun WidgetNarrowOffDarkPreview() {
        WidgetScreenshotCard(
            data = widgetData(name = "Small widget with a long name", isOn = false, color = BLUE_ARGB),
            isDark = true,
            isNarrow = true,
        )
    }

    @PreviewTest
    @Preview(widthDp = WIDE_WIDTH_DP, heightDp = WIDGET_HEIGHT_DP, showBackground = true)
    @Composable
    fun WidgetWideOfflineLightPreview() {
        WidgetScreenshotCard(
            data = widgetData(name = "Offline device", isOn = false, isOnline = false, color = ORANGE_ARGB),
            isDark = false,
        )
    }

    @PreviewTest
    @Preview(widthDp = WIDE_WIDTH_DP, heightDp = WIDGET_HEIGHT_DP, showBackground = true)
    @Composable
    fun WidgetWideQuickActionsLightPreview() {
        WidgetScreenshotCard(
            data = widgetData(color = CHARTREUSE_ARGB, quickActionsEnabled = true),
            isDark = false,
        )
    }

    private fun widgetData(
        name: String = "WLED Device",
        isOn: Boolean = true,
        isOnline: Boolean = true,
        color: Int = DEEP_SKY_BLUE_ARGB,
        quickActionsEnabled: Boolean = false,
    ) = WidgetStateData(
        macAddress = "aabbccddeeff",
        address = "192.168.1.101",
        name = name,
        isOn = isOn,
        isOnline = isOnline,
        color = color,
        lastUpdated = FIXED_TIMESTAMP,
        quickActionsEnabled = quickActionsEnabled,
    )
}
