package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WledWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)

        provideContent {
            GlanceTheme {
                WidgetContent(context, appWidgetId)
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                WidgetPreviewContent()
            }
        }
    }
}

class WledWidgetReceiver :
    GlanceAppWidgetReceiver(),
    KoinComponent {
    private val widgetManager: WledWidgetManager by inject()

    override val glanceAppWidget: GlanceAppWidget = WledWidget()

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
                widgetManager.updateAllWidgets(context)
            } finally {
                pendingResult?.finish()
            }
        }
    }
}
