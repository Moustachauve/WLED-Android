package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClient
import com.squareup.moshi.Moshi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

private const val TAG = "DeviceWebsocketListViewModel"

@HiltViewModel
class DeviceWebsocketListViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val deviceUpdateManager: DeviceUpdateManager,
    private val okHttpClient: OkHttpClient,
    private val moshi: Moshi
) : ViewModel(), DefaultLifecycleObserver {
    private val activeClients = MutableStateFlow<Map<String, WebsocketClient>>(emptyMap())
    private val devicesFromDb = deviceRepository.allDevices

    val showOfflineDevicesLast = userPreferencesRepository.showOfflineDevicesLast.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false
    )
    val showHiddenDevices = userPreferencesRepository.showHiddenDevices.stateIn(
        scope = viewModelScope, started = SharingStarted.WhileSubscribed(5000), initialValue = false
    )

    // Track if the ViewModel is paused or not. It would be paused if the app is in the
    // background, for example.
    private val isPaused = MutableStateFlow(false)

    init {
        // Observe the ProcessLifecycle (App level) instead of Activity. This ensures onPause is
        // only called when the *entire app* goes background, not when the screen rotates.
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        viewModelScope.launch {
            devicesFromDb.scan(emptyMap<String, WebsocketClient>()) { currentClients, newDeviceList ->
                // Create a mutable copy of the current client map to build the next state.
                val nextClients = currentClients.toMutableMap()
                val newDeviceMap = newDeviceList.associateBy { it.macAddress }

                // 1. Identify and destroy clients for devices that are no longer present.
                val devicesToRemove = currentClients.keys - newDeviceMap.keys
                devicesToRemove.forEach { macAddress ->
                    Log.d(TAG, "[Scan] Device removed: $macAddress. Destroying client.")
                    nextClients[macAddress]?.destroy()
                    nextClients.remove(macAddress)
                }

                // 2. Identify and create/update clients for new or changed devices.
                newDeviceMap.forEach { (macAddress, device) ->
                    val existingClient = currentClients[macAddress]
                    if (existingClient == null) {
                        // Device added: create and connect a new client.
                        Log.d(TAG, "[Scan] Device added: $macAddress. Creating client.")
                        val newClient = WebsocketClient(
                            device, deviceRepository, deviceUpdateManager, okHttpClient, moshi
                        )
                        if (!isPaused.value) {
                            newClient.connect()
                        }
                        nextClients[macAddress] = newClient
                    } else if (existingClient.deviceState.device.address != device.address) {
                        // Device IP changed: reconnect the client.
                        Log.d(
                            TAG,
                            "[Scan] Device address changed for $macAddress. Reconnecting client."
                        )
                        existingClient.destroy()
                        val newClient = WebsocketClient(
                            device, deviceRepository, deviceUpdateManager, okHttpClient, moshi
                        )
                        if (!isPaused.value) {
                            newClient.connect()
                        }
                        nextClients[macAddress] = newClient
                    } else {
                        existingClient.updateDevice(device)
                        nextClients[macAddress] = existingClient
                    }
                }
                // Return the updated map, which becomes `currentClients` for the next iteration.
                nextClients
            }.flowOn(Dispatchers.IO).collect { updatedClients ->
                // Emit the new map of clients to the StateFlow.
                activeClients.value = updatedClients
            }

        }
    }

    /**
     * Pauses all active WebSocket connections.
     * Called when the app goes into the background.
     */
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.d(TAG, "onPause: App is in the background. Pausing all connections.")
        isPaused.value = true
        activeClients.value.values.forEach { it.disconnect() }
    }

    /**
     * Resumes all active WebSocket connections.
     * Called when the app comes into the foreground.
     */
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d(TAG, "onResume: App is in the foreground. Resuming all connections.")
        isPaused.value = false
        activeClients.value.values.forEach { it.connect() }
    }

    /**
     * List of all devices with their real-time state.
     */
    val allDevicesWithState: StateFlow<List<DeviceWithState>> = activeClients.map { clients ->
        clients.values.map { it.deviceState }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    override fun onCleared() {
        super.onCleared()
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        Log.d(TAG, "ViewModel cleared. Closing all WebSocket clients.")
        activeClients.value.values.forEach { it.destroy() }
    }

    /**
     * Attempts to reconnect to all offline devices.
     */
    fun refreshOfflineDevices() {
        Log.d(TAG, "Refreshing offline devices.")
        val offlineClients = activeClients.value.values.filter { !it.deviceState.isOnline }
        offlineClients.forEach {
            it.connect()
        }
    }

    /**
     * Sets the brightness for a specific device.
     *
     * @param device The device to update.
     * @param brightness The brightness value to set (0-255).
     */
    fun setBrightness(device: DeviceWithState, brightness: Int) {
        viewModelScope.launch {
            val client = activeClients.value[device.device.macAddress]
            if (client == null) {
                Log.w(
                    TAG,
                    "setBrightness: No active client found for MAC address ${device.device.macAddress}"
                )
                return@launch
            }
            Log.d(TAG, "Setting brightness for $device.device.macAddress to $brightness")
            client.sendState(State(brightness = brightness))
        }
    }

    fun setDevicePower(device: DeviceWithState, isOn: Boolean) {
        viewModelScope.launch {
            val client = activeClients.value[device.device.macAddress]
            if (client == null) {
                Log.w(
                    TAG,
                    "setDevicePower: No active client found for MAC address ${device.device.macAddress}"
                )
                return@launch
            }
            Log.d(TAG, "Setting isOn for $device.device.macAddress to $isOn")
            client.sendState(State(isOn = isOn))
        }
    }

    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            Log.d(TAG, "Deleting device ${device.originalName} - ${device.address}")
            deviceRepository.delete(device)
        }
    }
}