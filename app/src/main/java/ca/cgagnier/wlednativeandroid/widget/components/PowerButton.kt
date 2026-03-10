package ca.cgagnier.wlednativeandroid.widget.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.unit.ColorProvider
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.widget.TogglePowerAction
import ca.cgagnier.wlednativeandroid.widget.brightenColor

private const val GLOW_BRIGHTNESS_FACTOR = 0.9f
private const val OUTLINE_BRIGHTNESS_FACTOR = 0.9f

@Composable
fun PowerButton(isOn: Boolean) {
    Box(
        modifier = GlanceModifier
            .size(48.dp) // Total size including glow
            .cornerRadius(24.dp)
            .clickable(
                actionRunCallback<TogglePowerAction>(
                    actionParametersOf(
                        TogglePowerAction.keyIsOn to isOn,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (isOn) {
            true -> PowerButtonOnState()
            false -> PowerButtonOffState()
        }
    }
}

@Composable
private fun PowerButtonOnState() {
    val context = LocalContext.current
    val buttonColor = GlanceTheme.colors.primary
    val onButtonColor = GlanceTheme.colors.onPrimary

    val primaryColorArgb = buttonColor.getColor(context).toArgb()
    val glowColorProvider = ColorProvider(
        Color(brightenColor(primaryColorArgb, GLOW_BRIGHTNESS_FACTOR)),
    )
    val outlineColorProvider = ColorProvider(
        Color(brightenColor(primaryColorArgb, OUTLINE_BRIGHTNESS_FACTOR)),
    )

    // Glow Layer (Outer, brighter neon)
    Image(
        provider = ImageProvider(R.drawable.widget_power_glow),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        colorFilter = ColorFilter.tint(glowColorProvider),
    )
    // Background Layer (Solid Primary)
    Image(
        provider = ImageProvider(R.drawable.widget_circle_fill),
        contentDescription = null,
        modifier = GlanceModifier.size(32.dp),
        colorFilter = ColorFilter.tint(buttonColor),
    )
    // Border Layer (Brighter Neon Outline)
    Image(
        provider = ImageProvider(R.drawable.widget_circle_outline),
        contentDescription = null,
        modifier = GlanceModifier.size(36.dp),
        colorFilter = ColorFilter.tint(outlineColorProvider),
    )

    // Icon Layer (OnPrimary)
    Image(
        provider = ImageProvider(R.drawable.outline_power_settings_new_24),
        contentDescription = "Toggle Power",
        modifier = GlanceModifier.size(20.dp),
        colorFilter = ColorFilter.tint(onButtonColor),
    )
}

@Composable
private fun PowerButtonOffState() {
    val onButtonColor = GlanceTheme.colors.onSurfaceVariant

    // OFF State: Simple icon, no background, no outline, no glow
    Image(
        provider = ImageProvider(R.drawable.outline_power_settings_new_24),
        contentDescription = "Toggle Power",
        modifier = GlanceModifier.size(20.dp),
        colorFilter = ColorFilter.tint(onButtonColor),
    )
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 120, heightDp = 70)
@Composable
private fun WidgetPowerButtonPreview() {
    GlanceTheme {
        Row {
            PowerButton(isOn = true)
            PowerButton(isOn = false)
        }
    }
}
