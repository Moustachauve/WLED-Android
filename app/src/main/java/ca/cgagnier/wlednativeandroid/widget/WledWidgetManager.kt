package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.ui.theme.getColorFromDeviceState
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WledWidgetManager @Inject constructor(
    private val deviceApiFactory: DeviceApiFactory,
    private val deviceRepository: DeviceRepository,
) {
    companion object {
        private const val TAG = "WledWidgetManager"
    }

    suspend fun updateAllWidgets(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(WledWidget::class.java)
        glanceIds.forEach { glanceId ->
            refreshWidget(context, glanceId)
        }
    }

    suspend fun refreshWidget(context: Context, glanceId: GlanceId) {
        val stateData = getWidgetState(context, glanceId) ?: return

        try {
            sendRequestAndUpdateData(stateData, context, glanceId)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Exception updating widget ${stateData.address}", e)
            val newData = stateData.copy(isOnline = false, lastUpdated = System.currentTimeMillis())
            saveStateAndPush(context, glanceId, newData)
        }
    }

    suspend fun toggleState(context: Context, glanceId: GlanceId, targetState: Boolean) {
        val stateData = getWidgetState(context, glanceId) ?: return

        try {
            sendRequestAndUpdateData(
                stateData,
                context,
                glanceId,
                JsonPost(isOn = targetState, verbose = true),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Exception toggling widget ${stateData.macAddress}", e)
            val newData = stateData.copy(isOnline = false, lastUpdated = System.currentTimeMillis())
            saveStateAndPush(context, glanceId, newData)
        }
    }

    private suspend fun sendRequestAndUpdateData(
        widgetData: WidgetStateData,
        context: Context,
        glanceId: GlanceId,
        jsonPost: JsonPost = JsonPost(verbose = true),
    ) {
        val device = deviceRepository.findDeviceByMacAddress(widgetData.macAddress)
        val targetAddress = device?.address ?: widgetData.address

        val api = deviceApiFactory.create(targetAddress)
        val response = api.postJson(jsonPost)

        if (response.isSuccessful) {
            response.body()?.let { body ->
                val newData = widgetData.copy(
                    address = targetAddress,
                    isOn = body.isOn ?: jsonPost.isOn ?: widgetData.isOn,
                    color = getColorFromDeviceState(body),
                    isOnline = true,
                    lastUpdated = System.currentTimeMillis(),
                )
                saveStateAndPush(context, glanceId, newData)
            }
        }
    }

    private suspend fun getWidgetState(context: Context, glanceId: GlanceId): WidgetStateData? {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val jsonString = prefs[WIDGET_DATA_KEY] ?: return null
        return try {
            Json.decodeFromString<WidgetStateData>(jsonString)
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse widget data", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to parse widget data", e)
            null
        }
    }

    private suspend fun saveStateAndPush(context: Context, glanceId: GlanceId, data: WidgetStateData) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WIDGET_DATA_KEY] = Json.encodeToString(data)
        }
        WledWidget().update(context, glanceId)
    }
}
