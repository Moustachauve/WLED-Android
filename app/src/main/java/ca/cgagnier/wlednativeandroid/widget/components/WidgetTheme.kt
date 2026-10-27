package ca.cgagnier.wlednativeandroid.widget.components

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import ca.cgagnier.wlednativeandroid.widget.WidgetStateData
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import androidx.glance.material3.ColorProviders as createColorProviders

private const val LIGHT_ON_BACKGROUND_TINT = 0.16f
private const val DARK_ON_BACKGROUND_TINT = 0.20f
private const val LIGHT_OFF_BACKGROUND_TINT = 0.05f
private const val DARK_OFF_BACKGROUND_TINT = 0.06f
private const val PILL_SURFACE_TINT = 0.10f

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
 * Uses a soft Material 3 tonal surface for the widget background so that the rounded-square
 * power button and its ambient side glow stand out with clear text contrast in both light and
 * dark themes.
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

    val baseLight = dynamicColorScheme(
        seedColor = seedColor,
        isDark = false,
        style = style,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    val baseDark = dynamicColorScheme(
        seedColor = seedColor,
        isDark = true,
        style = style,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

    val neutralLight = dynamicColorScheme(
        seedColor = seedColor,
        isDark = false,
        style = PaletteStyle.Neutral,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
    val neutralDark = dynamicColorScheme(
        seedColor = seedColor,
        isDark = true,
        style = PaletteStyle.Neutral,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )

    val lightScheme = refineWidgetColorScheme(
        base = baseLight,
        neutral = neutralLight,
        widgetState = widgetState,
        isDark = false,
    )
    val darkScheme = refineWidgetColorScheme(
        base = baseDark,
        neutral = neutralDark,
        widgetState = widgetState,
        isDark = true,
    )

    return createColorProviders(
        light = lightScheme,
        dark = darkScheme,
    )
}

private fun refineWidgetColorScheme(
    base: ColorScheme,
    neutral: ColorScheme,
    widgetState: WidgetStateData,
    isDark: Boolean,
): ColorScheme {
    val bgTintFactor = when {
        !widgetState.isOnline -> 0f
        widgetState.isOn -> if (isDark) DARK_ON_BACKGROUND_TINT else LIGHT_ON_BACKGROUND_TINT
        else -> if (isDark) DARK_OFF_BACKGROUND_TINT else LIGHT_OFF_BACKGROUND_TINT
    }
    val pillTintFactor = if (widgetState.isOnline && widgetState.isOn) PILL_SURFACE_TINT else 0f

    val cardBackground = lerp(neutral.surfaceContainerLow, base.primary, bgTintFactor)
    val pillSurface = lerp(neutral.surfaceContainerHighest, base.primary, pillTintFactor)

    return base.copy(
        surface = cardBackground,
        primaryContainer = cardBackground,
        surfaceVariant = pillSurface,
        secondaryContainer = pillSurface,
        onSurface = neutral.onSurface,
        onSurfaceVariant = neutral.onSurfaceVariant,
        outline = neutral.outlineVariant,
    )
}
