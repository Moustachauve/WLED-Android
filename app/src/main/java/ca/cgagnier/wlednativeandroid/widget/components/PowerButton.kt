package ca.cgagnier.wlednativeandroid.widget.components

import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.widget.TogglePowerAction

private val WIDE_BUTTON_SIZE = 52.dp
private val WIDE_CORNER_RADIUS = 16.dp
private val WIDE_ICON_SIZE = 24.dp

private val COMPACT_BUTTON_SIZE = 40.dp
private val COMPACT_CORNER_RADIUS = 13.dp
private val COMPACT_ICON_SIZE = 20.dp

@Composable
fun PowerButton(isOn: Boolean, isCompact: Boolean = false) {
    val buttonSize = if (isCompact) COMPACT_BUTTON_SIZE else WIDE_BUTTON_SIZE
    val cornerRadius = if (isCompact) COMPACT_CORNER_RADIUS else WIDE_CORNER_RADIUS
    val iconSize = if (isCompact) COMPACT_ICON_SIZE else WIDE_ICON_SIZE
    val fillRes = if (isCompact) R.drawable.widget_squircle_compact_fill else R.drawable.widget_squircle_fill
    val outlineRes = if (isCompact) R.drawable.widget_squircle_compact_outline else R.drawable.widget_squircle_outline
    val toggleDescription = LocalContext.current.getString(R.string.widget_toggle_power)

    Box(
        modifier = GlanceModifier
            .size(buttonSize)
            .cornerRadius(cornerRadius)
            .clickable(
                actionRunCallback<TogglePowerAction>(
                    actionParametersOf(
                        TogglePowerAction.keyIsOn to isOn,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isOn) {
            Image(
                provider = ImageProvider(fillRes),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            )
            Image(
                provider = ImageProvider(R.drawable.outline_power_settings_new_24),
                contentDescription = toggleDescription,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
            )
        } else {
            Image(
                provider = ImageProvider(fillRes),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.surfaceVariant),
            )
            Image(
                provider = ImageProvider(outlineRes),
                contentDescription = null,
                modifier = GlanceModifier.fillMaxSize(),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.outline),
            )
            Image(
                provider = ImageProvider(R.drawable.outline_power_settings_new_24),
                contentDescription = toggleDescription,
                modifier = GlanceModifier.size(iconSize),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 140, heightDp = 70)
@Composable
private fun WidgetPowerButtonPreview() {
    GlanceTheme {
        Row {
            PowerButton(isOn = true)
            Spacer(modifier = GlanceModifier.width(8.dp))
            PowerButton(isOn = false)
        }
    }
}
