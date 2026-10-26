package ca.cgagnier.wlednativeandroid.widget.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import ca.cgagnier.wlednativeandroid.widget.WidgetStateData
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import androidx.glance.material3.ColorProviders as createColorProviders

/**
 * A widget theme based on the current state of a device.
 */
@Composable
fun WLEDWidgetTheme(widgetState: WidgetStateData, content: @Composable () -> Unit) {
    GlanceTheme(
        colors = createDeviceColorProviders(widgetState),
        content = content,
    )
}

/**
 * Creates a Material 3 [ColorScheme] for a widget based on the device's current state and LED color.
 */
private fun createWidgetColorScheme(widgetState: WidgetStateData, isDark: Boolean): ColorScheme {
    val style = when {
        !widgetState.isOnline -> PaletteStyle.Neutral
        !widgetState.isOn -> PaletteStyle.TonalSpot
        else -> PaletteStyle.Fidelity
    }
    val seedColor = if (widgetState.color != -1) {
        Color(widgetState.color)
    } else {
        Color.White
    }

    return dynamicColorScheme(
        seedColor = seedColor,
        isDark = isDark,
        style = style,
        isAmoled = isDark, // Use deeper/more saturated colors for dark mode
        specVersion = if (isDark) ColorSpec.SpecVersion.Default else ColorSpec.SpecVersion.SPEC_2025,
    )
}

/**
 * Creates a [ColorProviders] for the widget themed with the device's current LED color.
 *
 * @param widgetState The state of the widget.
 */
private fun createDeviceColorProviders(widgetState: WidgetStateData): ColorProviders = createColorProviders(
    light = createWidgetColorScheme(widgetState, isDark = false),
    dark = createWidgetColorScheme(widgetState, isDark = true),
)
