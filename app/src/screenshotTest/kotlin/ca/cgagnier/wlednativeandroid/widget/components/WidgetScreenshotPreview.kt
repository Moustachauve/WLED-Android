package ca.cgagnier.wlednativeandroid.widget.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.widget.DEFAULT_QUICK_ACTION_LABELS
import ca.cgagnier.wlednativeandroid.widget.WidgetStateData
import ca.cgagnier.wlednativeandroid.widget.brightenColor

private const val GLOW_BRIGHTNESS_FACTOR = 0.9f
private const val OUTLINE_BRIGHTNESS_FACTOR = 0.9f
private val WIDGET_SAFE_PADDING = 12.dp
private val WIDGET_CORNER_RADIUS = 16.dp
private val QUICK_ACTION_BUTTON_HEIGHT = 28.dp
private val QUICK_ACTION_CORNER_RADIUS = 12.dp
private val QUICK_ACTION_SEPARATOR_PADDING = 6.dp

/**
 * Compose UI mirror of [ca.cgagnier.wlednativeandroid.widget.WidgetContent] used for deterministic
 * host-side screenshot testing (`com.android.compose.screenshot`).
 *
 * Glance widgets render via `RemoteViews` (`AndroidRemoteViews` for `Chronometer` and `<shape>`
 * XML drawables for the power button glow/fill/outline), which cannot be inflated by the JVM
 * Compose Preview `painterResource` renderer. Keep this composable in sync when modifying
 * [ca.cgagnier.wlednativeandroid.widget.WidgetContent] or [PowerButton].
 */
@Composable
internal fun WidgetScreenshotCard(
    data: WidgetStateData,
    isDark: Boolean = false,
    isNarrow: Boolean = false,
    elapsedTimeText: String = "0:00",
    quickActionLabels: List<String> = DEFAULT_QUICK_ACTION_LABELS,
) {
    val colorScheme = createWidgetColorScheme(data, isDark = isDark)

    MaterialTheme(colorScheme = colorScheme) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(WIDGET_CORNER_RADIUS))
                .background(colorScheme.surfaceVariant),
            contentAlignment = Alignment.TopEnd,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(WIDGET_SAFE_PADDING),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isNarrow) {
                        WidgetNarrowBody(data = data)
                    } else {
                        WidgetWideBody(data = data, quickActionLabels = quickActionLabels)
                    }
                }
            }
            WidgetRefreshButton()
            WidgetElapsedTimeBadge(text = elapsedTimeText)
        }
    }
}

@Composable
private fun WidgetWideBody(data: WidgetStateData, quickActionLabels: List<String>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetDeviceDetails(
                data = data,
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
                showAddress = true,
            )
            WidgetPowerButton(isOn = data.isOn)
        }
        if (data.quickActionsEnabled && quickActionLabels.isNotEmpty()) {
            WidgetQuickActionRow(labels = quickActionLabels)
        }
    }
}

@Composable
private fun WidgetNarrowBody(data: WidgetStateData) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WidgetPowerButton(isOn = data.isOn)
        WidgetDeviceDetails(
            data = data,
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            showAddress = false,
        )
    }
}

@Composable
private fun WidgetDeviceDetails(
    data: WidgetStateData,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    showAddress: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = data.name,
            color = colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showAddress) {
                Text(
                    text = data.address,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!data.isOnline) {
                if (showAddress) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Image(
                    painter = painterResource(R.drawable.twotone_signal_wifi_connected_no_internet_0_24),
                    contentDescription = stringResource(R.string.is_offline),
                    modifier = Modifier.size(12.dp),
                    colorFilter = ColorFilter.tint(colorScheme.error),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.is_offline),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun WidgetPowerButton(isOn: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isOn) {
            val primaryArgb = colorScheme.primary.toArgb()
            val glowColor = Color(brightenColor(primaryArgb, GLOW_BRIGHTNESS_FACTOR))
            val outlineColor = Color(brightenColor(primaryArgb, OUTLINE_BRIGHTNESS_FACTOR))

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor, Color.Transparent),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(colorScheme.primary, CircleShape),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .padding(1.dp)
                    .border(2.dp, outlineColor, CircleShape),
            )
            Image(
                painter = painterResource(R.drawable.outline_power_settings_new_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(colorScheme.onPrimary),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.outline_power_settings_new_24),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun WidgetQuickActionRow(labels: List<String>) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(QUICK_ACTION_BUTTON_HEIGHT)
            .clip(RoundedCornerShape(QUICK_ACTION_CORNER_RADIUS))
            .background(colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(vertical = QUICK_ACTION_SEPARATOR_PADDING)
                        .width(1.dp)
                        .background(colorScheme.outline),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun WidgetRefreshButton() {
    val colorScheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.padding(8.dp)) {
        Image(
            painter = painterResource(R.drawable.outline_refresh_24),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .padding(4.dp),
            colorFilter = ColorFilter.tint(colorScheme.outline),
        )
    }
}

@Composable
private fun WidgetElapsedTimeBadge(text: String) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 2.dp, end = 20.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.outline_history_24),
                contentDescription = null,
                modifier = Modifier.size(8.dp),
                colorFilter = ColorFilter.tint(colorScheme.onSurfaceVariant),
            )
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = text,
                color = colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                lineHeight = 10.sp,
            )
        }
    }
}
