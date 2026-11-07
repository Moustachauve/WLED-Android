package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback

// Both actions hand off to WidgetActionWorker: the callback itself may not have network access.
class TogglePowerAction : ActionCallback {

    companion object {
        val keyIsOn = ActionParameters.Key<Boolean>("isOn")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentIsOn = parameters[keyIsOn] ?: false
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

        WidgetActionWorker.enqueue(context, WidgetActionRequest(appWidgetId, targetIsOn = !currentIsOn))
    }
}

class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)

        WidgetActionWorker.enqueue(context, WidgetActionRequest(appWidgetId, targetIsOn = null))
    }
}
