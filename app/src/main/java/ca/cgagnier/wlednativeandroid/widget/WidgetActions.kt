package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TogglePowerAction : ActionCallback {
    companion object {
        val keyIsOn = ActionParameters.Key<Boolean>("isOn")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val currentIsOn = parameters[keyIsOn] ?: false
        val newIsOn = !currentIsOn

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WledWidget.WidgetEntryPoint::class.java,
        )

        entryPoint.widgetManager().toggleState(context, glanceId, newIsOn)
    }
}

class RefreshAction : ActionCallback {
    companion object {
        val keyMacAddress = ActionParameters.Key<String>("macAddress")
    }

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WledWidget.WidgetEntryPoint::class.java,
        )
        withContext(Dispatchers.IO) {
            entryPoint.widgetManager().refreshWidget(context, glanceId)
        }
    }
}
