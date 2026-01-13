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
 * Creates a [ColorProviders] for the widget themed with the device's current LED color.
 *
 * @param widgetState The state of the widget.
 */
private fun createDeviceColorProviders(widgetState: WidgetStateData): ColorProviders {
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

    val lightScheme: ColorScheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = false,
        style = style,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

    val darkScheme: ColorScheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = true,
        style = style,
        isAmoled = true, // Use deeper/more saturated colors for dark mode
    )

    return createColorProviders(
        light = lightScheme,
        dark = darkScheme,
    )
}
