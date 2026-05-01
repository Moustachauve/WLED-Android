package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.Preset
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.runBlocking

class PresetWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory {
        return PresetWidgetRemoteViewsFactory(this.applicationContext, intent)
    }
}

class PresetWidgetRemoteViewsFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
        android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID
    )
    private val limit: Int = intent.getIntExtra(PresetWidgetProvider.EXTRA_LIST_LIMIT, 0)
    private val itemLayoutId: Int = intent.getIntExtra(PresetWidgetProvider.EXTRA_LAYOUT_ID, R.layout.widget_preset_item)
    private var presets: List<Pair<String, Preset>> = emptyList()
    private var deviceAddress: String? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PresetWidgetServiceEntryPoint {
        fun deviceApiFactory(): DeviceApiFactory
        fun moshi(): Moshi
    }

    override fun onCreate() {
        // Data loading should ideally happen in onDataSetChanged
    }

    private var selectedPresetId: Int = -1

    override fun onDataSetChanged() {
        deviceAddress = PresetWidgetConfigureActivity.loadDeviceAddress(context, appWidgetId)
        if (deviceAddress == null) {
            Log.e(TAG, "Device address is null")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            PresetWidgetServiceEntryPoint::class.java
        )
        val deviceApiFactory = entryPoint.deviceApiFactory()
        val moshi = entryPoint.moshi()

        runBlocking {
            try {
                Log.d(TAG, "Fetching presets for $deviceAddress")
                val api = deviceApiFactory.create(deviceAddress!!)
                
                // Fetch state to know selected preset
                val stateResponse = api.getState()
                if (stateResponse.isSuccessful) {
                    val state = stateResponse.body()
                    if (state != null) {
                        selectedPresetId = state.selectedPresetId ?: -1
                        if (selectedPresetId == -1 && state.selectedPlaylistId != null && state.selectedPlaylistId > 0) {
                             selectedPresetId = state.selectedPlaylistId
                        }
                    }
                }

                // Fetch Presets
                val response = api.getPresets()
                if (response.isSuccessful) {
                    val presetsMap = response.body()
                    Log.d(TAG, "Presets fetched: ${presetsMap?.size}")
                    if (presetsMap != null) {
                         savePresetsToCache(presetsMap, moshi)
                         processPresets(presetsMap)
                    }
                } else {
                    Log.e(TAG, "Error fetching presets: ${response.code()}")
                    loadPresetsFromCache(moshi)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching presets", e)
                loadPresetsFromCache(moshi)
            }
        }
    }

    private fun processPresets(presetsMap: Map<String, Preset>) {
        var sortedPresets = presetsMap.entries
            .filter { it.key != "0" }
            .map { it.key to it.value }
            .sortedBy { it.first.toIntOrNull() ?: Int.MAX_VALUE }
       
       if (limit > 0) {
           sortedPresets = sortedPresets.take(limit)
       }
       presets = sortedPresets
    }

    private fun savePresetsToCache(presetsMap: Map<String, Preset>, moshi: Moshi) {
        try {
            val type = Types.newParameterizedType(Map::class.java, String::class.java, Preset::class.java)
            val adapter = moshi.adapter<Map<String, Preset>>(type)
            val json = adapter.toJson(presetsMap)
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            prefs.putString(PREF_CACHE_PRESETS + appWidgetId, json)
            prefs.putInt(PREF_CACHE_SELECTED_ID + appWidgetId, selectedPresetId)
            prefs.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save presets to cache", e)
        }
    }

    private fun loadPresetsFromCache(moshi: Moshi) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString(PREF_CACHE_PRESETS + appWidgetId, null)
            selectedPresetId = prefs.getInt(PREF_CACHE_SELECTED_ID + appWidgetId, -1)
            
            if (json != null) {
                val type = Types.newParameterizedType(Map::class.java, String::class.java, Preset::class.java)
                val adapter = moshi.adapter<Map<String, Preset>>(type)
                val presetsMap = adapter.fromJson(json)
                if (presetsMap != null) {
                    Log.d(TAG, "Loaded presets from cache for $appWidgetId")
                    processPresets(presetsMap)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load presets from cache", e)
        }
    }
    
    // ... (rest of the file)


    override fun onDestroy() {
        presets = emptyList()
    }

    override fun getCount(): Int {
        return presets.size
    }



    // ... (rest of class)

    override fun getViewAt(position: Int): RemoteViews {
        if (position >= presets.size) return RemoteViews(context.packageName, itemLayoutId)

        val (id, preset) = presets[position]
        val views = RemoteViews(context.packageName, itemLayoutId)
        
        var name = preset.name
        if (name.isEmpty()) {
            name = "Preset $id"
        }
        
        views.setTextViewText(R.id.preset_name, name)
        
        if (id.toIntOrNull() == selectedPresetId) {
            if (itemLayoutId == R.layout.widget_preset_button_item) {
                views.setInt(R.id.widget_item, "setBackgroundResource", R.drawable.widget_button_selected)
            } else {
                views.setViewVisibility(R.id.preset_indicator, android.view.View.VISIBLE)
            }
        } else {
            if (itemLayoutId == R.layout.widget_preset_button_item) {
                views.setInt(R.id.widget_item, "setBackgroundResource", R.drawable.widget_background)
            } else {
                views.setViewVisibility(R.id.preset_indicator, android.view.View.GONE)
            }
        }

        val fillInIntent = Intent().apply {
            putExtra(PresetWidgetProvider.EXTRA_PRESET_ID, id.toIntOrNull() ?: -1)
            putExtra(PresetWidgetProvider.EXTRA_DEVICE_ADDRESS, deviceAddress)
        }
        views.setOnClickFillInIntent(R.id.widget_item, fillInIntent)

        return views
    }

    override fun getLoadingView(): RemoteViews? {
        return null
    }

    override fun getViewTypeCount(): Int {
        return 1
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun hasStableIds(): Boolean {
        return true
    }

    companion object {
        private const val TAG = "PresetWidgetService"
        private const val PREFS_NAME = "ca.cgagnier.wlednativeandroid.widget.PresetWidgetCache"
        private const val PREF_CACHE_PRESETS = "cache_presets_"
        private const val PREF_CACHE_SELECTED_ID = "cache_selected_id_"
    }
}
