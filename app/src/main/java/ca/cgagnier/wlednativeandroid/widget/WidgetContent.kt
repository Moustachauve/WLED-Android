package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProviders
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
import androidx.glance.unit.ColorProvider
import ca.cgagnier.wlednativeandroid.R
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
            GlanceTheme(colors = getWidgetTheme(data)) {
                DeviceWidgetContent(data)
            }
        }

        else -> ErrorState(context, appWidgetId)
    }
}

/**
 * Create device-colored theme from the LED color
 */
@Composable
private fun getWidgetTheme(widgetState: WidgetStateData): ColorProviders {
    val seedColor = if (widgetState.color != -1) {
        Color(widgetState.color)
    } else {
        Color.White
    }
    val deviceColorProviders = createDeviceColorProviders(
        seedColor = seedColor,
        isOnline = widgetState.isOnline,
    )
    return deviceColorProviders
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
                PowerButton(data)
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
            PowerButton(data)
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

private const val GLOW_BRIGHTNESS_FACTOR = 0.2f
private const val OUTLINE_BRIGHTNESS_FACTOR = 0.5f

@Composable
private fun PowerButton(data: WidgetStateData) {
    val buttonColor = GlanceTheme.colors.primary
    val onButtonColor = GlanceTheme.colors.onPrimary
    val buttonOffColor = GlanceTheme.colors.surfaceVariant
    val onButtonOffColor = GlanceTheme.colors.onSurfaceVariant

    val context = LocalContext.current
    val primaryColorArgb = buttonColor.getColor(context).toArgb()

    val glowColorProvider = ColorProvider(
        Color(brightenColor(primaryColorArgb, GLOW_BRIGHTNESS_FACTOR)),
    )
    val outlineColorProvider = ColorProvider(
        Color(brightenColor(primaryColorArgb, OUTLINE_BRIGHTNESS_FACTOR)),
    )

    Box(
        modifier = GlanceModifier
            .size(48.dp) // Total size including glow
            .cornerRadius(24.dp)
            .clickable(
                actionRunCallback<TogglePowerAction>(
                    actionParametersOf(
                        TogglePowerAction.keyIsOn to data.isOn,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // TODO: The colors should be defined directly in the appropriate states, not decided by parent?
        if (data.isOn) {
            PowerButtonOnState(glowColorProvider, buttonColor, outlineColorProvider, onButtonColor)
        } else {
            PowerButtonOffState(buttonOffColor, onButtonOffColor)
        }
    }
}

@Composable
private fun PowerButtonOnState(
    glowColor: ColorProvider,
    buttonColor: ColorProvider,
    outlineColor: ColorProvider,
    iconColor: ColorProvider,
) {
    // Glow Layer (Outer, brighter neon)
    Image(
        provider = ImageProvider(R.drawable.widget_power_glow),
        contentDescription = null,
        modifier = GlanceModifier.fillMaxSize(),
        colorFilter = ColorFilter.tint(glowColor),
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
        colorFilter = ColorFilter.tint(outlineColor),
    )

    // Icon Layer (OnPrimary)
    Image(
        provider = ImageProvider(R.drawable.outline_power_settings_new_24),
        contentDescription = "Toggle Power",
        modifier = GlanceModifier.size(20.dp),
        colorFilter = ColorFilter.tint(iconColor),
    )
}

@Composable
private fun PowerButtonOffState(buttonColor: ColorProvider, iconColor: ColorProvider) {
    // OFF State: Simple solid surfaceVariant, no outline, no glow
    Image(
        provider = ImageProvider(R.drawable.widget_circle_fill),
        contentDescription = null,
        modifier = GlanceModifier.size(32.dp),
        colorFilter = ColorFilter.tint(buttonColor),
    )
    Image(
        provider = ImageProvider(R.drawable.outline_power_settings_new_24),
        contentDescription = "Toggle Power",
        modifier = GlanceModifier.size(20.dp),
        colorFilter = ColorFilter.tint(iconColor),
    )
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
    // Small bottom adding to be near the bottom edge, bigger end padding to be safe from the corner radius
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(bottom = 2.dp, end = 18.dp),
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
    val seedColor = Color(0xFF0000FF) // Blue
    val colorProviders = createDeviceColorProviders(seedColor = seedColor, isOnline = true)
    GlanceTheme(colors = colorProviders) {
        DeviceWidgetContent(
            data = WidgetStateData(
                macAddress = "AA:BB:CC:DD:EE:FF",
                address = "192.168.1.100",
                name = "WLED Device",
                isOn = true,
                isOnline = true,
                color = 0xFF0000FF.toInt(),
            ),
        )
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 100, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewNarrow() {
    val seedColor = Color(0xFF0000FF) // Blue
    val colorProviders = createDeviceColorProviders(seedColor = seedColor, isOnline = true)
    GlanceTheme(colors = colorProviders) {
        DeviceWidgetContent(
            data = WidgetStateData(
                macAddress = "AA:BB:CC:DD:EE:FF",
                address = "192.168.1.100",
                name = "Small widget with a long name",
                isOn = true,
                isOnline = true,
                color = 0xFF0000FF.toInt(),
            ),
        )
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewOff() {
    val seedColor = Color(0xFFFF8000) // Orange
    val colorProviders = createDeviceColorProviders(seedColor = seedColor, isOnline = false)
    GlanceTheme(colors = colorProviders) {
        DeviceWidgetContent(
            data = WidgetStateData(
                macAddress = "AA:BB:CC:DD:EE:FF",
                address = "192.168.1.101",
                name = "Offline device",
                isOn = false,
                isOnline = false,
                color = 0xFFFF8000.toInt(),
            ),
        )
    }
}

@Suppress("MagicNumber")
@OptIn(ExperimentalGlancePreviewApi::class)
@Preview(widthDp = 200, heightDp = 100)
@Composable
private fun DeviceWidgetContentPreviewQuickActions() {
    val seedColor = Color(0xFFDFFF00) // Chartreuse
    val colorProviders = createDeviceColorProviders(seedColor = seedColor, isOnline = true)
    GlanceTheme(colors = colorProviders) {
        DeviceWidgetContent(
            data = WidgetStateData(
                macAddress = "AA:BB:CC:DD:EE:FF",
                address = "192.168.1.100",
                name = "WLED Device",
                isOn = true,
                isOnline = true,
                color = 0xFF0000FF.toInt(),
                quickActionsEnabled = true,
            ),
        )
    }
}
