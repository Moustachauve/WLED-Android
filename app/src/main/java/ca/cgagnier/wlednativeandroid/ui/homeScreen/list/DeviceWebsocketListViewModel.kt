package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceWebsocketListViewModel"

@HiltViewModel
class DeviceWebsocketListViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    userPreferencesRepository: UserPreferencesRepository
) : ViewModel(), DefaultLifecycleObserver {
    private val showHiddenDevices = userPreferencesRepository.showHiddenDevices
    private val activeClients = MutableStateFlow<Map<String, WebsocketClient>>(emptyMap())
    private val devicesFromDb = deviceRepository.allDevices

    // Track if the ViewModel is paused or not. It would be paused if the app is in the
    // background, for example.
    private val isPaused = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            devicesFromDb
                .scan(emptyMap<String, WebsocketClient>()) { currentClients, newDeviceList ->
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
                            val newClient = WebsocketClient(device, deviceRepository)
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
                            val newClient = WebsocketClient(device, deviceRepository)
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
                }
                .collect { updatedClients ->
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

    // This is the unfiltered list of devices with their real-time state.
    private val allDevicesWithState: StateFlow<List<DeviceWithState>> =
        activeClients.map { clients ->
            clients.values.map { it.deviceState }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val devicesWithState: StateFlow<List<DeviceWithState>> =
        combine(allDevicesWithState, showHiddenDevices) { devices, showHidden ->
            // Handles the preference to show or hide hidden devices
            if (showHidden) {
                devices
            } else {
                devices.filter { !it.device.isHidden }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Determine if the "some devices are hidden" message should be shown.
    val shouldShowDevicesAreHidden: StateFlow<Boolean> =
        combine(devicesWithState, showHiddenDevices) { filteredDevices, showHidden ->
            // Message appears if:
            // 1. The *filtered* list is empty.
            // 2. The user has chosen *not* to show hidden devices.
            // 3. There is at least one hidden device in the database.
            if (filteredDevices.isEmpty() && !showHidden) {
                deviceRepository.hasHiddenDevices()
            } else {
                false
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared. Closing all WebSocket clients.")
        activeClients.value.values.forEach { it.destroy() }
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
}