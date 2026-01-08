package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.IOException

class WledWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun deviceRepository(): DeviceRepository
        fun okHttpClient(): OkHttpClient
    }
}

class WledWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WledWidget()

    companion object {
        private const val TAG = "WledWidgetReceiver"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()

        // Refresh state for all widgets
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    try {
                        val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(
                            context,
                        ).getGlanceIdBy(appWidgetId)
                        val prefs = androidx.glance.appwidget.state.getAppWidgetState(
                            context,
                            androidx.glance.state.PreferencesGlanceStateDefinition,
                            glanceId,
                        )
                        val jsonString = prefs[WIDGET_DATA_KEY]
                        val widgetData = if (jsonString != null) {
                            try {
                                kotlinx.serialization.json.Json.decodeFromString<WidgetStateData>(jsonString)
                            } catch (e: Exception) {
                                null
                            }
                        } else null

                        widgetData?.let { currentData ->
                            val entryPoint = EntryPointAccessors.fromApplication(
                                context,
                                WledWidget.WidgetEntryPoint::class.java,
                            )
                            val client = entryPoint.okHttpClient()
                            val deviceApiFactory = DeviceApiFactory(client)
                            val api = deviceApiFactory.create(currentData.address)
                            try {
                                val response = api.postJson(JsonPost(verbose = true))
                                if (response.isSuccessful) {
                                    response.body()?.let { body ->
                                        val newData = currentData.copy(
                                            isOn = body.isOn ?: currentData.isOn,
                                            // Update other fields if your API response contains them
                                            lastUpdated = System.currentTimeMillis()
                                        )
                                        androidx.glance.appwidget.state.updateAppWidgetState(
                                            context,
                                            glanceId,
                                        ) { prefs ->
                                            prefs[WIDGET_DATA_KEY] = kotlinx.serialization.json.Json.encodeToString(newData)
                                        }
                                        WledWidget().update(context, glanceId)
                                    }
                                }
                            } catch (e: IOException) {
                                Log.e(TAG, "Failed to update widget state for ${currentData.address}", e)
                            }
                        }
                    } catch (e: IllegalArgumentException) {
                        Log.e(TAG, "Failed to update widget $appWidgetId", e)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to update widget $appWidgetId", e)
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
