package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WledWidgetManager @Inject constructor(
    private val deviceApiFactory: DeviceApiFactory
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
            val api = deviceApiFactory.create(stateData.address)
            // Fetch status
            val response = api.postJson(JsonPost(verbose = true))

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    val newData = stateData.copy(
                        isOn = body.isOn ?: stateData.isOn,
                        color = -1, // logic to extract color if needed
                        lastUpdated = System.currentTimeMillis()
                    )
                    saveStateAndPush(context, glanceId, newData)
                }
            } else {
                Log.e(TAG, "Error updating widget ${stateData.address}: ${response.code()}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception updating widget ${stateData.address}", e)
        }
    }

    suspend fun toggleState(context: Context, glanceId: GlanceId, targetState: Boolean) {
        val stateData = getWidgetState(context, glanceId) ?: return

        try {
            val api = deviceApiFactory.create(stateData.address)
            val response = api.postJson(JsonPost(isOn = targetState, verbose = true))

            if (response.isSuccessful) {
                response.body()?.let { body ->
                    val newData = stateData.copy(
                        isOn = body.isOn ?: targetState,
                        lastUpdated = System.currentTimeMillis()
                    )
                    saveStateAndPush(context, glanceId, newData)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception toggling widget ${stateData.address}", e)
        }
    }

    private suspend fun getWidgetState(context: Context, glanceId: GlanceId): WidgetStateData? {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val jsonString = prefs[WIDGET_DATA_KEY] ?: return null
        return try {
            Json.decodeFromString<WidgetStateData>(jsonString)
        } catch (e: Exception) {
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