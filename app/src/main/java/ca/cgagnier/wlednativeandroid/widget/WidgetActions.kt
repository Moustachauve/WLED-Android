package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

class TogglePowerAction :
    ActionCallback,
    KoinComponent {
    companion object {
        val keyIsOn = ActionParameters.Key<Boolean>("isOn")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentIsOn = parameters[keyIsOn] ?: false
        val newIsOn = !currentIsOn

        val widgetManager: WledWidgetManager = get()
        widgetManager.toggleState(context, glanceId, newIsOn)
    }
}

class RefreshAction :
    ActionCallback,
    KoinComponent {

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetManager: WledWidgetManager = get()
        withContext(Dispatchers.IO) {
            widgetManager.refreshWidget(context, glanceId)
        }
    }
}
