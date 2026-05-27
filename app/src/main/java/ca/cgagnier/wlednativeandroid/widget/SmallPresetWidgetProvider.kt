package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import ca.cgagnier.wlednativeandroid.R

class SmallPresetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            PresetWidgetProvider.updateAppWidget(
                context,
                appWidgetManager,
                appWidgetId,
                limit = 3,
                layoutId = R.layout.widget_preset_horizontal,
                listId = R.id.preset_grid,
                itemLayoutId = R.layout.widget_preset_button_item,
                titleViewId = null,
            )
        }
    }
}
