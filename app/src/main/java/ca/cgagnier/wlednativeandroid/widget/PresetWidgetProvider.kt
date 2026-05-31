package ca.cgagnier.wlednativeandroid.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class PresetWidgetProvider : AppWidgetProvider() {

    @Inject
    lateinit var deviceApiFactory: DeviceApiFactory

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_TRIGGER_PRESET) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            val presetId = intent.getIntExtra(EXTRA_PRESET_ID, -1)
            val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
            val listId = intent.getIntExtra(EXTRA_LIST_ID, R.id.preset_list)

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID && presetId != -1 && deviceAddress != null) {
                triggerPreset(context, deviceAddress, presetId, appWidgetId, listId)
            }
        }

        if (intent.action == ACTION_REFRESH) {
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            val listId = intent.getIntExtra(EXTRA_LIST_ID, R.id.preset_list)
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                PresetWidgetRemoteViewsFactory.forceRefresh(appWidgetId)
                val appWidgetManager = AppWidgetManager.getInstance(context)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, listId)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun triggerPreset(context: Context, deviceAddress: String, presetId: Int, appWidgetId: Int, listId: Int) {
        Log.d(TAG, "triggerPreset called for widget $appWidgetId, preset $presetId, device $deviceAddress")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = deviceApiFactory.create(deviceAddress)
                val response = api.postState(State(selectedPresetId = presetId))

                if (response.isSuccessful) {
                    Log.d(TAG, "Successfully triggered preset $presetId for widget $appWidgetId")
                    val prefs = context.getSharedPreferences(
                        "ca.cgagnier.wlednativeandroid.widget.PresetWidgetCache",
                        Context.MODE_PRIVATE,
                    ).edit()
                    prefs.putInt("cache_selected_id_" + appWidgetId, presetId)
                    prefs.apply()

                    PresetWidgetRemoteViewsFactory.forceRefresh(appWidgetId)

                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, listId)
                    Log.d(TAG, "Triggered widget view update for $appWidgetId, listId $listId")
                } else {
                    Log.e(TAG, "Failed to trigger preset: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering preset", e)
            }
        }
    }

    companion object {
        const val ACTION_TRIGGER_PRESET = "ca.cgagnier.wlednativeandroid.widget.ACTION_TRIGGER_PRESET"
        const val ACTION_REFRESH = "ca.cgagnier.wlednativeandroid.widget.ACTION_REFRESH"
        const val EXTRA_PRESET_ID = "ca.cgagnier.wlednativeandroid.widget.EXTRA_PRESET_ID"
        const val EXTRA_DEVICE_ADDRESS = "ca.cgagnier.wlednativeandroid.widget.EXTRA_DEVICE_ADDRESS"
        const val EXTRA_LIST_LIMIT = "ca.cgagnier.wlednativeandroid.widget.EXTRA_LIST_LIMIT"
        const val EXTRA_LAYOUT_ID = "ca.cgagnier.wlednativeandroid.widget.EXTRA_LAYOUT_ID"
        const val EXTRA_LIST_ID = "ca.cgagnier.wlednativeandroid.widget.EXTRA_LIST_ID"
        private const val TAG = "PresetWidgetProvider"

        @Suppress("LongParameterList")
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            limit: Int = 0,
            layoutId: Int = R.layout.widget_preset,
            listId: Int = R.id.preset_list,
            itemLayoutId: Int = R.layout.widget_preset_item,
            titleViewId: Int? = R.id.appwidget_text,
            providerClass: Class<out AppWidgetProvider> = PresetWidgetProvider::class.java,
        ) {
            val deviceName = PresetWidgetConfigureActivity.loadDeviceName(context, appWidgetId)
            val deviceAddress = PresetWidgetConfigureActivity.loadDeviceAddress(context, appWidgetId)

            // Construct the RemoteViews object
            val views = RemoteViews(context.packageName, layoutId)

            val configureIntent = Intent(context, PresetWidgetConfigureActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val configurePendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configureIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            if (deviceAddress == null) {
                titleViewId?.let {
                    views.setTextViewText(it, context.getString(R.string.widget_please_configure))
                }
                views.setEmptyView(listId, R.id.empty_view)
                views.setOnClickPendingIntent(R.id.empty_view, configurePendingIntent)
                titleViewId?.let {
                    views.setOnClickPendingIntent(it, configurePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                return
            }

            titleViewId?.let {
                views.setTextViewText(it, deviceName)
                views.setOnClickPendingIntent(it, configurePendingIntent)
            }

            // Set up the collection
            val intent = Intent(context, PresetWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_LIST_LIMIT, limit)
                putExtra(EXTRA_LAYOUT_ID, itemLayoutId)
                putExtra(EXTRA_LIST_ID, listId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(listId, intent)
            views.setEmptyView(listId, R.id.empty_view)

            val refreshIntent = Intent(context, providerClass).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_LIST_ID, listId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setOnClickPendingIntent(R.id.empty_view, refreshPendingIntent)

            // Set up pending intent template for items
            val toastIntent = Intent(context, providerClass).apply {
                action = ACTION_TRIGGER_PRESET
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(EXTRA_LIST_ID, listId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            val toastPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                toastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(listId, toastPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, listId)
        }
    }
}
