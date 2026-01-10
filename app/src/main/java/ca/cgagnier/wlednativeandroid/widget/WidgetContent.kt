package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.SwitchDefaults
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.preview.ExperimentalGlancePreviewApi
import androidx.glance.preview.Preview
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.serialization.json.Json

val WIDGET_DATA_KEY = stringPreferencesKey("widget_data")

@Composable
fun WidgetContent(context: Context, appWidgetId: Int) {
    val prefs = currentState<Preferences>()
    val jsonString = prefs[WIDGET_DATA_KEY]

    val data = try {
        if (jsonString != null) Json.decodeFromString<WidgetStateData>(jsonString) else null
    } catch (_: Exception) {
        null
    }
    if (data == null) {
        ErrorState(context, appWidgetId)
        return
    }

    // Create device-colored theme from the LED color
    val seedColor = if (data.color != -1) {
        Color(data.color)
    } else {
        Color.White
    }
    val deviceColorProviders = createDeviceColorProviders(
        seedColor = seedColor,
        isOnline = data.isOn, // Use isOn as a proxy for online status
    )

    GlanceTheme(colors = deviceColorProviders) {
        DeviceWidgetContent(data)
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
    val intent = Intent(
        LocalContext.current,
        MainActivity::class.java,
    ).apply {
        putExtra(MainActivity.EXTRA_DEVICE_MAC_ADDRESS, data.macAddress)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .padding(16.dp)
            .clickable(actionStartActivity(intent)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight(),
        ) {
            Text(
                text = data.name,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = data.address,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant),
                maxLines = 1,
            )
            Text(
                text = data.lastUpdatedFormatted,
                style = TextStyle(
                    color = GlanceTheme.colors.outline,
                    fontSize = 10.sp,
                ),
            )
        }
        Switch(
            checked = data.isOn,
            onCheckedChange = actionRunCallback<TogglePowerAction>(
                actionParametersOf(
                    TogglePowerAction.keyIsOn to data.isOn,
                ),
            ),
            colors = SwitchDefaults.colors(
                checkedThumbColor = GlanceTheme.colors.primary,
                checkedTrackColor = GlanceTheme.colors.primary,
                uncheckedThumbColor = GlanceTheme.colors.primary,
                uncheckedTrackColor = GlanceTheme.colors.primary,
            ),
        )
    }
}

class TogglePowerAction : ActionCallback {
    companion object {
        val keyIsOn = ActionParameters.Key<Boolean>("isOn")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentIsOn = parameters[keyIsOn] ?: false
        val newIsOn = !currentIsOn

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WledWidget.WidgetEntryPoint::class.java,
        )

        entryPoint.widgetManager().toggleState(context, glanceId, newIsOn)
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
                name = "WLED Status",
                isOn = false,
                color = 0xFFFF8000.toInt(),
            ),
        )
    }
}
