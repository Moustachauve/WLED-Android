package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.dp
import androidx.glance.Button
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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

    override fun onUpdate(
        context: Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        val pendingResult = goAsync()
        // Refresh state for all widgets
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { appWidgetId ->
                    try {
                        val glanceId = androidx.glance.appwidget.GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
                        val prefs = androidx.glance.appwidget.state.getAppWidgetState(context, androidx.glance.state.PreferencesGlanceStateDefinition, glanceId)
                        val address = prefs[DEVICE_ADDRESS_KEY]

                        if (address != null) {
                            val entryPoint = EntryPointAccessors.fromApplication(
                                context,
                                WledWidget.WidgetEntryPoint::class.java
                            )
                            val client = entryPoint.okHttpClient()
                            val deviceApiFactory = DeviceApiFactory(client)
                            val api = deviceApiFactory.create(address!!)
                            try {
                                val response = api.postJson(JsonPost(verbose = true))
                                if (response.isSuccessful && response.body() != null) {
                                    val isOn = response.body()!!.state.isOn ?: false
                                    androidx.glance.appwidget.state.updateAppWidgetState(context, glanceId) { prefs ->
                                        prefs[DEVICE_IS_ON_KEY] = isOn
                                    }
                                    WledWidget().update(context, glanceId)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
