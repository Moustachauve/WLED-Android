package ca.cgagnier.wlednativeandroid.widget

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProviders
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import androidx.glance.material3.ColorProviders as createColorProviders

/**
 * Creates a [ColorProviders] for the widget themed with the device's current LED color.
 *
 * @param seedColor The device's current LED color to use as the seed for the color scheme
 * @param isOnline Whether the device is currently online (affects the palette style)
 */
fun createDeviceColorProviders(seedColor: Color, isOnline: Boolean): ColorProviders {
    val style = if (isOnline) PaletteStyle.Vibrant else PaletteStyle.Neutral

    val lightScheme: ColorScheme = dynamicColorScheme(
        seedColor = seedColor,
        isDark = false,
        style = style,
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
