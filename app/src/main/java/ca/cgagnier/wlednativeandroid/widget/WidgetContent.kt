package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.widget.components.ElapsedTimeChronometer
import ca.cgagnier.wlednativeandroid.widget.components.PowerButton
import ca.cgagnier.wlednativeandroid.widget.components.QuickActionButtons
import ca.cgagnier.wlednativeandroid.widget.components.QuickActionItem
import ca.cgagnier.wlednativeandroid.widget.components.WLEDWidgetTheme
import kotlinx.serialization.json.Json

val WIDGET_DATA_KEY = stringPreferencesKey("widget_data")
private val NARROW_WIDGET_WIDTH_THRESHOLD = 150.dp
private val WIDGET_SAFE_PADDING = 12.dp

@Composable
fun WidgetContent(context: Context, appWidgetId: Int) {
    val prefs = currentState<Preferences>()
    val jsonString = prefs[WIDGET_DATA_KEY]

    val data = try {
        if (jsonString != null) Json.decodeFromString<WidgetStateData>(jsonString) else null
    } catch (_: Exception) {
        null
    }

    when (data) {
        is WidgetStateData -> {
            WLEDWidgetTheme(data) {
                DeviceWidgetContent(data)
            }
        }

        else -> ErrorState(context, appWidgetId)
    }
}

@Composable
private fun ErrorState(context: Context, appWidgetId: Int) {
    // Error State: Make it clickable to open the configuration
    val configureIntent = Intent(context, WledWidgetConfigureActivity::class.java).apply {
        putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(8.dp)
            .clickable(actionStartActivity(configureIntent)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = context.getString(R.string.widget_please_configure),
            style = TextStyle(color = GlanceTheme.colors.error),
        )
    }
}

@Composable
private fun DeviceWidgetContent(data: WidgetStateData) {
    val size = LocalSize.current
    // Threshold for switching between narrow and wide layouts.
    // Standard cell width ~57dp-73dp. 110dp min width in xml implies ~2 cells.
    // Let's assume < 150dp is narrow (compact), >= 150dp is wide (row).
    val isNarrow = size.width < NARROW_WIDGET_WIDTH_THRESHOLD

    if (isNarrow) {
        DeviceWidgetContentNarrow(data)
    } else {
        DeviceWidgetContentWide(data)
    }
}

@Composable
private fun DeviceWidgetContentWide(data: WidgetStateData) {
    DeviceWidgetContainer(data) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                DeviceDetailsColumn(
                    data = data,
                    modifier = GlanceModifier.defaultWeight(),
                    showAddress = true,
                )
                // Switch stays next to content in Wide mode
                PowerButton(isOn = data.isOn)
            }
            // TODO: Add a way for users to configure quick actions
            if (data.quickActionsEnabled) {
                QuickActionButtons(
                    items = listOf(
                        QuickActionItem(label = "a1", onClick = actionRunCallback<RefreshAction>()),
                        QuickActionItem(label = "b2", onClick = actionRunCallback<RefreshAction>()),
                        QuickActionItem(label = "c3", onClick = actionRunCallback<RefreshAction>()),
                        QuickActionItem(label = "d4", onClick = actionRunCallback<RefreshAction>()),
                    ),
                )
            }
        }
    }
}

@Composable
private fun DeviceWidgetContentNarrow(data: WidgetStateData) {
    DeviceWidgetContainer(data) {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PowerButton(isOn = data.isOn)
            DeviceDetailsColumn(
                data = data,
                modifier = GlanceModifier.defaultWeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                showAddress = false,
            )
        }
    }
}

@Composable
private fun DeviceWidgetContainer(data: WidgetStateData, content: @Composable () -> Unit) {
    val intent = data.toOpenWidgetInAppIntent(LocalContext.current)

    Box(
        modifier = GlanceModifier.fillMaxSize(),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(WIDGET_SAFE_PADDING)
                .clickable(actionStartActivity(intent)),
        ) {
            // Main content takes available space
            Box(
                modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
        RefreshButton()
        ElapsedTimeChronometerContainer(data.lastUpdated)
    }
}

@Composable
private fun DeviceDetailsColumn(
    data: WidgetStateData,
    modifier: GlanceModifier = GlanceModifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    showAddress: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
    ) {
        Text(
            text = data.name,
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showAddress) {
                Text(
                    text = data.address,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                    maxLines = 1,
                )
            }
            if (!data.isOnline) {
                if (showAddress) {
                    Spacer(modifier = GlanceModifier.width(8.dp))
                }
                Image(
                    provider = ImageProvider(R.drawable.twotone_signal_wifi_connected_no_internet_0_24),
                    contentDescription = LocalContext.current.getString(R.string.is_offline),
                    modifier = GlanceModifier.size(12.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.error),
                )
                Spacer(modifier = GlanceModifier.width(4.dp))
                Text(
                    text = LocalContext.current.getString(R.string.is_offline),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RefreshButton(modifier: GlanceModifier = GlanceModifier) {
    Box(modifier = modifier.padding(8.dp)) {
        Image(
            provider = ImageProvider(R.drawable.outline_refresh_24),
            contentDescription = "Refresh",
            modifier = GlanceModifier
                .size(20.dp)
                .padding(4.dp)
                .clickable(
                    actionRunCallback<RefreshAction>(),
                ),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.outline),
        )
    }
}

@Composable
private fun ElapsedTimeChronometerContainer(lastUpdated: Long) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            // Small bottom padding to be near the bottom edge, bigger end padding to be safe from the corner radius
            .padding(bottom = 2.dp, end = 20.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        ElapsedTimeChronometer(lastUpdated)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewOn() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "WLED Device",
        isOn = true,
        isOnline = true,
        color = 0xFF0000FF.toInt(), // Blue
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 100, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewNarrow() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "Small widget with a long name",
        isOn = false,
        isOnline = true,
        color = 0xFF0000FF.toInt(), // Blue
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewOff() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.101",
        name = "Offline device",
        isOn = false,
        isOnline = false,
        color = 0xFFFF8000.toInt(), // Orange
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewQuickActions() {
    val widgetData = WidgetStateData(
        macAddress = "AA:BB:CC:DD:EE:FF",
        address = "192.168.1.100",
        name = "WLED Device",
        isOn = true,
        isOnline = true,
        color = 0xFFDFFF00.toInt(), // Chartreuse
        quickActionsEnabled = true,
    )
    WLEDWidgetTheme(widgetData) {
        DeviceWidgetContent(widgetData)
    }
}
