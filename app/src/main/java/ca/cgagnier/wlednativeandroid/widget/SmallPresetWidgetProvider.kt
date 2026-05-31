package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import ca.cgagnier.wlednativeandroid.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class SmallPresetWidgetProvider : PresetWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(
                context,
                appWidgetManager,
                appWidgetId,
                limit = 3,
                layoutId = R.layout.widget_preset_horizontal,
                listId = R.id.preset_grid,
                itemLayoutId = R.layout.widget_preset_button_item,
                titleViewId = null,
                providerClass = SmallPresetWidgetProvider::class.java,
            )
        }
    }
}
