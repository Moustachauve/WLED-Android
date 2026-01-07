package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.Switch
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.currentState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

val DEVICE_ADDRESS_KEY = stringPreferencesKey("device_address")
val DEVICE_NAME_KEY = stringPreferencesKey("device_name")
val DEVICE_IS_ON_KEY = booleanPreferencesKey("device_is_on")

@Composable
fun WidgetContent(context: Context) {
    val prefs = currentState<Preferences>()
    val deviceAddress = prefs[DEVICE_ADDRESS_KEY]
    val deviceName = prefs[DEVICE_NAME_KEY] ?: context.getString(ca.cgagnier.wlednativeandroid.R.string.widget_select_device)
    val isOn = prefs[DEVICE_IS_ON_KEY] ?: false

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = GlanceModifier.defaultWeight()
        ) {
            Text(
                text = deviceName,
                style = TextStyle(color = ColorProvider(Color.Black)),
            )
            if (deviceAddress != null) {
                Text(
                    text = deviceAddress,
                    style = TextStyle(color = ColorProvider(Color.DarkGray)),
                )
            } else {
                Text(
                    text = context.getString(ca.cgagnier.wlednativeandroid.R.string.widget_please_configure),
                    style = TextStyle(color = ColorProvider(Color.Red))
                )
            }
        }

        deviceAddress?.let { address ->
            Switch(
                checked = isOn,
                onCheckedChange = actionRunCallback<TogglePowerAction>(
                    actionParametersOf(
                        TogglePowerAction.keyAddress to address,
                        TogglePowerAction.keyIsOn to isOn
                    )
                )
            )
        }
    }
}

class TogglePowerAction : ActionCallback {
    companion object {
        private const val TAG = "TogglePowerAction"
        val keyAddress = ActionParameters.Key<String>("address")
        val keyIsOn = ActionParameters.Key<Boolean>("isOn")
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val address = parameters[keyAddress] ?: return
        // Note: The toggle action toggles based on the *current* state known to the widget.
        // If the widget is out of sync, this might be incorrect, but the API call sets the absolute state.
        val currentIsOn = parameters[keyIsOn] ?: false
        val newIsOn = !currentIsOn

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WledWidget.WidgetEntryPoint::class.java
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
                        prefs[DEVICE_IS_ON_KEY] = updatedIsOn
                    }
                    WledWidget().update(context, glanceId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle power", e)
        }
    }
}
