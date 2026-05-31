package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.wledapi.Preset
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

class PresetWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        PresetWidgetRemoteViewsFactory(this.applicationContext, intent)
}

@Suppress("TooGenericExceptionCaught", "MagicNumber")
class PresetWidgetRemoteViewsFactory(private val context: Context, intent: Intent) :
    RemoteViewsService.RemoteViewsFactory {

    private val appWidgetId: Int = intent.getIntExtra(
        android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
        android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID,
    )
    private val limit: Int = intent.getIntExtra(PresetWidgetProvider.EXTRA_LIST_LIMIT, 0)
    private val itemLayoutId: Int = intent.getIntExtra(
        PresetWidgetProvider.EXTRA_LAYOUT_ID,
        R.layout.widget_preset_item,
    )
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

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    override fun onDataSetChanged() {
        Log.d(TAG, "onDataSetChanged called for widget $appWidgetId")
        deviceAddress = PresetWidgetConfigureActivity.loadDeviceAddress(context, appWidgetId)
        if (deviceAddress == null) {
            Log.e(TAG, "Device address is null for widget $appWidgetId")
            return
        }

        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            PresetWidgetServiceEntryPoint::class.java,
        )
        val deviceApiFactory = entryPoint.deviceApiFactory()
        val moshi = entryPoint.moshi()

        // Always load from cache first to ensure immediate display of existing data
        loadPresetsFromCache(moshi)
        Log.d(TAG, "Loaded ${presets.size} presets from cache for widget $appWidgetId")

        // Throttle check
        val currentTime = System.currentTimeMillis()
        val lastFetchTime = lastFetchTimeMap[appWidgetId] ?: 0L
        val isForce = forceRefreshMap[appWidgetId] ?: false

        if (!isForce && currentTime - lastFetchTime < 15000L) {
            Log.d(TAG, "Skipping network fetch for $appWidgetId, throttled")
            return
        }

        // Reset force flag and update last fetch time
        forceRefreshMap[appWidgetId] = false
        lastFetchTimeMap[appWidgetId] = currentTime

        runBlocking {
            val address = deviceAddress ?: PresetWidgetConfigureActivity.loadDeviceAddress(context, appWidgetId)
            if (address == null) {
                Log.e(TAG, "Device address is null in runBlocking (widget $appWidgetId)")
                return@runBlocking
            }
            deviceAddress = address

            try {
                Log.d(TAG, "Fetching presets for $deviceAddress (widget $appWidgetId)")
                val api = deviceApiFactory.create(deviceAddress!!, 4L)

                val stateDeferred = async(Dispatchers.IO) { api.getState() }
                val presetsDeferred = async(Dispatchers.IO) { api.getPresets() }

                val stateResponse = try {
                    stateDeferred.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching state concurrently (widget $appWidgetId)", e)
                    null
                }

                if (stateResponse != null && stateResponse.isSuccessful) {
                    val state = stateResponse.body()
                    Log.d(TAG, "State fetched successfully for widget $appWidgetId")
                    if (state != null) {
                        selectedPresetId = state.selectedPresetId ?: -1
                        if (selectedPresetId == -1 && state.selectedPlaylistId != null &&
                            state.selectedPlaylistId > 0
                        ) {
                            selectedPresetId = state.selectedPlaylistId
                        }
                    }
                } else {
                    Log.e(TAG, "State response failed for widget $appWidgetId: ${stateResponse?.code()}")
                }

                val presetsResponse = try {
                    presetsDeferred.await()
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching presets concurrently (widget $appWidgetId)", e)
                    null
                }

                if (presetsResponse != null && presetsResponse.isSuccessful) {
                    val presetsMap = presetsResponse.body()
                    Log.d(TAG, "Presets fetched: ${presetsMap?.size} for widget $appWidgetId")
                    if (presetsMap != null) {
                        savePresetsToCache(presetsMap, moshi)
                        processPresets(presetsMap)
                    }
                } else {
                    Log.e(TAG, "Error fetching presets for widget $appWidgetId: ${presetsResponse?.code() ?: "null response"}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching presets for widget $appWidgetId", e)
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
                    Log.d(TAG, "Loaded presets from cache for $appWidgetId. Selected: $selectedPresetId")
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

    override fun getCount(): Int = presets.size

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

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true

    companion object {
        private const val TAG = "PresetWidgetService"
        private const val PREFS_NAME = "ca.cgagnier.wlednativeandroid.widget.PresetWidgetCache"
        private const val PREF_CACHE_PRESETS = "cache_presets_"
        private const val PREF_CACHE_SELECTED_ID = "cache_selected_id_"

        private val lastFetchTimeMap = java.util.concurrent.ConcurrentHashMap<Int, Long>()
        private val forceRefreshMap = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

        fun forceRefresh(appWidgetId: Int) {
            forceRefreshMap[appWidgetId] = true
        }
    }
}
