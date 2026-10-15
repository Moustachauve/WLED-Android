package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.WidgetSnapshot
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.ui.theme.getColorFromDeviceState
import ca.cgagnier.wlednativeandroid.widget.components.getDeviceName
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal interface WidgetStorage {
    suspend fun getGlanceIds(context: Context): List<GlanceId>
    suspend fun getWidgetState(context: Context, glanceId: GlanceId): WidgetStateData?
    suspend fun saveStateAndPush(context: Context, glanceId: GlanceId, data: WidgetStateData)
    suspend fun clearStateAndPush(context: Context, glanceId: GlanceId)
}

internal object GlanceWidgetStorage : WidgetStorage {
    private const val TAG = "GlanceWidgetStorage"

    override suspend fun getGlanceIds(context: Context): List<GlanceId> =
        GlanceAppWidgetManager(context).getGlanceIds(WledWidget::class.java)

    override suspend fun getWidgetState(context: Context, glanceId: GlanceId): WidgetStateData? {
        val prefs = getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)
        val jsonString = prefs[WIDGET_DATA_KEY] ?: return null
        return try {
            Json.decodeFromString<WidgetStateData>(jsonString)
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse widget data", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Failed to parse widget data", e)
            null
        }
    }

    override suspend fun saveStateAndPush(context: Context, glanceId: GlanceId, data: WidgetStateData) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs[WIDGET_DATA_KEY] = Json.encodeToString(data)
        }
        WledWidget().update(context, glanceId)
    }

    override suspend fun clearStateAndPush(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, glanceId) { prefs ->
            prefs.remove(WIDGET_DATA_KEY)
        }
        WledWidget().update(context, glanceId)
    }
}

class WledWidgetManager internal constructor(
    private val deviceApiFactory: DeviceApiFactory,
    private val deviceRepository: DeviceRepository,
    private val widgetStorage: WidgetStorage,
) {
    constructor(
        deviceApiFactory: DeviceApiFactory,
        deviceRepository: DeviceRepository,
    ) : this(deviceApiFactory, deviceRepository, GlanceWidgetStorage)

    companion object {
        private const val TAG = "WledWidgetManager"
        internal const val WIDGET_REQUEST_TIMEOUT_SECONDS = 5L
    }

    /**
     * Updates all widgets for a device based on a projected [WidgetSnapshot]
     * received from an external source (e.g., WebSocket state sync).
     *
     * @param context Application context
     * @param snapshot The projected widget snapshot for the device
     */
    suspend fun updateWidgetsFromSnapshot(context: Context, snapshot: WidgetSnapshot) {
        // Create a complete, authoritative state from scratch
        // This ensures all widgets for the device get the exact same state
        val newData = WidgetStateData(
            macAddress = snapshot.macAddress,
            address = snapshot.address,
            name = snapshot.name,
            isOn = snapshot.isOn,
            isOnline = snapshot.isOnline,
            color = snapshot.color,
            lastUpdated = snapshot.lastUpdated,
        )

        Log.d(TAG, "Updating widgets from snapshot for MAC ${snapshot.macAddress}")
        updateWidgetsForMacAddress(context, snapshot.macAddress, newData)
    }

    suspend fun updateAllWidgets(context: Context) = coroutineScope {
        val glanceIds = widgetStorage.getGlanceIds(context)
        glanceIds.forEach { glanceId ->
            launch {
                refreshWidget(context, glanceId)
            }
        }
    }

    suspend fun refreshWidget(context: Context, glanceId: GlanceId) {
        val stateData = widgetStorage.getWidgetState(context, glanceId) ?: return

        try {
            sendRequestAndUpdateData(stateData, context)
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Exception updating widget ${stateData.address}", e)
            val newData = stateData.copy(isOnline = false, lastUpdated = System.currentTimeMillis())
            updateWidgetsForMacAddress(context, stateData.macAddress, newData)
        }
    }

    suspend fun toggleState(context: Context, glanceId: GlanceId, targetState: Boolean) {
        val stateData = widgetStorage.getWidgetState(context, glanceId) ?: return

        try {
            sendRequestAndUpdateData(
                stateData,
                context,
                JsonPost(isOn = targetState, verbose = true),
            )
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Exception toggling widget ${stateData.macAddress}", e)
            val newData = stateData.copy(isOnline = false, lastUpdated = System.currentTimeMillis())
            updateWidgetsForMacAddress(context, stateData.macAddress, newData)
        }
    }

    private suspend fun sendRequestAndUpdateData(
        widgetData: WidgetStateData,
        context: Context,
        jsonPost: JsonPost = JsonPost(verbose = true),
    ) {
        val device = deviceRepository.findDeviceByMacAddress(widgetData.macAddress)
        val targetAddress = device?.address ?: widgetData.address

        val api = deviceApiFactory.create(targetAddress, WIDGET_REQUEST_TIMEOUT_SECONDS)
        val response = api.postJson(jsonPost)

        val body = response.body
        if (response.isSuccessful && body != null) {
            val newData = widgetData.copy(
                address = targetAddress,
                name = getDeviceName(device, context.getString(R.string.default_device_name)),
                isOn = body.isOn ?: jsonPost.isOn ?: widgetData.isOn,
                color = getColorFromDeviceState(body),
                isOnline = true,
                lastUpdated = System.currentTimeMillis(),
            )
            updateWidgetsForMacAddress(context, widgetData.macAddress, newData)
        } else {
            val newData = widgetData.copy(isOnline = false, lastUpdated = System.currentTimeMillis())
            updateWidgetsForMacAddress(context, widgetData.macAddress, newData)
        }
    }

    /**
     * Updates all widgets that share the given MAC address with the new state data.
     * This ensures that when a device's state changes, all widgets controlling that
     * device are updated simultaneously.
     */
    private suspend fun updateWidgetsForMacAddress(context: Context, macAddress: String, newData: WidgetStateData) {
        val glanceIds = widgetStorage.getGlanceIds(context)

        glanceIds.forEach { glanceId ->
            val widgetState = widgetStorage.getWidgetState(context, glanceId) ?: return@forEach
            if (widgetState.macAddress == macAddress) {
                Log.d(TAG, "Updating widget for MAC $macAddress")
                widgetStorage.saveStateAndPush(context, glanceId, newData)
            }
        }
    }

    /**
     * Updates the name and address displayed on all widgets for a device.
     * Call this when the device's customName, originalName or address changes.
     */
    suspend fun updateWidgetDeviceDetails(context: Context, device: Device) {
        val glanceIds = widgetStorage.getGlanceIds(context)
        val displayName = getDeviceName(device, context.getString(R.string.default_device_name))

        glanceIds.forEach { glanceId ->
            val widgetState = widgetStorage.getWidgetState(context, glanceId) ?: return@forEach
            if (widgetState.macAddress == device.macAddress) {
                Log.d(TAG, "Updating widget details for MAC ${device.macAddress} to $displayName at ${device.address}")
                val newData = widgetState.copy(name = displayName, address = device.address)
                widgetStorage.saveStateAndPush(context, glanceId, newData)
            }
        }
    }

    /**
     * Clears data for all widgets associated with a device.
     * Call this when a device is deleted from the app.
     * The widgets will show an error state prompting reconfiguration.
     */
    suspend fun deleteWidgetsForDevice(context: Context, macAddress: String) {
        val glanceIds = widgetStorage.getGlanceIds(context)

        glanceIds.forEach { glanceId ->
            val widgetState = widgetStorage.getWidgetState(context, glanceId) ?: return@forEach
            if (widgetState.macAddress == macAddress) {
                try {
                    // Clear state - widget will show error state prompting reconfiguration
                    widgetStorage.clearStateAndPush(context, glanceId)
                    Log.d(TAG, "Cleared widget data for deleted device: $macAddress")
                } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                    Log.e(TAG, "Error clearing widget for device $macAddress", e)
                }
            }
        }
    }
}
