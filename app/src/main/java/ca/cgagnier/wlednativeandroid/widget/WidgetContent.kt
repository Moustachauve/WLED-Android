package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import dagger.hilt.android.EntryPointAccessors
import kotlinx.serialization.json.Json
import java.io.IOException

val WIDGET_DATA_KEY = stringPreferencesKey("widget_data")

@Composable
fun WidgetContent(context: Context) {
    val prefs = currentState<Preferences>()
    val jsonString = prefs[WIDGET_DATA_KEY]
    val data = try {
        if (jsonString != null) Json.decodeFromString<WidgetStateData>(jsonString) else null
    } catch (_: Exception) {
        null
    }
    if (data == null) {
        Text(
            text = context.getString(ca.cgagnier.wlednativeandroid.R.string.widget_please_configure),
            style = TextStyle(color = ColorProvider(Color.Red)),
        )
        return
    }

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight(),
        ) {
            Text(
                text = data.name,
                style = TextStyle(color = ColorProvider(Color.Black)),
            )
            Text(
                text = data.address,
                style = TextStyle(color = ColorProvider(Color.DarkGray)),
            )
            Text(data.lastUpdatedFormatted)
        }
        Switch(
            checked = data.isOn,
            onCheckedChange = actionRunCallback<TogglePowerAction>(
                actionParametersOf(
                    TogglePowerAction.keyAddress to data.address,
                    TogglePowerAction.keyIsOn to data.isOn,
                ),
            ),
        )
    }
}

class TogglePowerAction : ActionCallback {
    companion object {
        private const val TAG = "TogglePowerAction"
        val keyAddress = ActionParameters.Key<String>("address")
        val keyIsOn = ActionParameters.Key<Boolean>("isOn")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val address = parameters[keyAddress] ?: return
        // Note: The toggle action toggles based on the *current* state known to the widget.
        // If the widget is out of sync, this might be incorrect, but the API call sets the absolute state.
        val currentIsOn = parameters[keyIsOn] ?: false
        val newIsOn = !currentIsOn

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WledWidget.WidgetEntryPoint::class.java,
        )
        val client = entryPoint.okHttpClient()
        val deviceApiFactory = DeviceApiFactory(client)
        val api = deviceApiFactory.create(address)

        try {
            val response = api.postJson(JsonPost(isOn = newIsOn, verbose = true))
            if (response.isSuccessful) {
                response.body()?.let { body ->
                    val updatedIsOn = body.isOn ?: newIsOn

                    updateAppWidgetState(context, glanceId) { prefs ->
                        val jsonString = prefs[WIDGET_DATA_KEY]
                        val currentData = if (jsonString != null) {
                            try {
                                Json.decodeFromString<WidgetStateData>(jsonString)
                            } catch (_: Exception) {
                                null
                            }
                        } else null

                        // Only proceed if we successfully recovered the existing data object
                        if (currentData != null) {
                            // Update the specific fields from the API response
                            val newData = currentData.copy(
                                isOn = updatedIsOn,
                                // Since we have the API response, we can also freely update
                                // other fields here if needed
                                lastUpdated = System.currentTimeMillis(),
                            )
                            prefs[WIDGET_DATA_KEY] = Json.encodeToString(newData)
                        }
                    }
                    WledWidget().update(context, glanceId)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to toggle power", e)
        }
    }
}
