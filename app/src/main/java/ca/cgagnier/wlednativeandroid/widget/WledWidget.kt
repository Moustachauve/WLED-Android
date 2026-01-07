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
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

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
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
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
                        val address = prefs[DEVICE_ADDRESS_KEY]

                        address?.let {
                            val entryPoint = EntryPointAccessors.fromApplication(
                                context,
                                WledWidget.WidgetEntryPoint::class.java,
                            )
                            val client = entryPoint.okHttpClient()
                            val deviceApiFactory = DeviceApiFactory(client)
                            val api = deviceApiFactory.create(it)
                            try {
                                val response = api.postJson(JsonPost(verbose = true))
                                if (response.isSuccessful) {
                                    response.body()?.let { body ->
                                        val isOn = body.isOn ?: false
                                        androidx.glance.appwidget.state.updateAppWidgetState(
                                            context,
                                            glanceId,
                                        ) { prefs ->
                                            prefs[DEVICE_IS_ON_KEY] = isOn
                                        }
                                        WledWidget().update(context, glanceId)
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to update widget state for $it", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update widget $appWidgetId", e)
                    }
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
