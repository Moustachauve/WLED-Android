package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClient
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG = "DeviceWebsocketListViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5000L

@HiltViewModel
@Suppress("LongParameterList", "TooGenericExceptionCaught") // DI constructor and lifecycle error handling
class DeviceWebsocketListViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
    private val deviceRepository: DeviceRepository,
    private val websocketClientFactory: WebsocketClientFactory,
    private val widgetManager: WledWidgetManager,
    private val saveDeviceStateUseCase: SaveDeviceStateUseCase,
    private val deviceUpdateManager: DeviceUpdateManager,
    @ApplicationContext private val applicationContext: Context,
) : ViewModel(),
    DefaultLifecycleObserver {

    private val activeClients = ConcurrentHashMap<String, WebsocketClient>()
    private val clientJobs = ConcurrentHashMap<String, Job>()

    private val devicesWithStateMap = MutableStateFlow<Map<String, DeviceWithState>>(emptyMap())

    val allDevicesWithState: StateFlow<List<DeviceWithState>> = devicesWithStateMap
        .map { it.values.toList() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = emptyList(),
        )

    private val devicesFromDb = deviceRepository.allDevices

    val showOfflineDevicesLast = userPreferencesRepository.showOfflineDevicesLast.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = false,
    )
    val showHiddenDevices = userPreferencesRepository.showHiddenDevices.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
        initialValue = false,
    )

    // Track if the ViewModel is paused or not. It would be paused if the app is in the background.
    private val isPaused = MutableStateFlow(false)

    init {
        // Observe ProcessLifecycle (App level) instead of Activity so onPause is
        // only called when the entire app goes to background.
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (e: Exception) {
            Log.w(TAG, "ProcessLifecycleOwner not available: ${e.message}")
        }

        viewModelScope.launch {
            devicesFromDb.collect { newDeviceList ->
                syncDevices(newDeviceList)
            }
        }
    }

    private fun syncDevices(newDeviceList: List<Device>) {
        val newDeviceMap = newDeviceList.associateBy { it.macAddress }

        // 1. Identify and destroy clients for devices that are no longer present.
        val devicesToRemove = activeClients.keys - newDeviceMap.keys
        for (macAddress in devicesToRemove) {
            Log.d(TAG, "[Sync] Device removed: $macAddress. Cancelling job and destroying client.")
            clientJobs.remove(macAddress)?.cancel()
            activeClients.remove(macAddress)?.destroy()
        }

        // 2. Identify and create/update clients for new or changed devices.
        for ((macAddress, device) in newDeviceMap) {
            val existingClient = activeClients[macAddress]
            if (existingClient == null) {
                // Device added: create, observe, and connect client
                Log.d(TAG, "[Sync] Device added: $macAddress (${device.address}). Creating client.")
                val newClient = websocketClientFactory.create(device)
                activeClients[macAddress] = newClient
                startObservingClient(newClient)
                if (!isPaused.value) {
                    newClient.connect()
                }
            } else if (existingClient.device.address != device.address) {
                // Device IP changed: reconnect client
                Log.d(
                    TAG,
                    "[Sync] Device address changed for $macAddress to ${device.address}. Reconnecting client.",
                )
                clientJobs.remove(macAddress)?.cancel()
                existingClient.destroy()

                val newClient = websocketClientFactory.create(device)
                activeClients[macAddress] = newClient
                startObservingClient(newClient)
                if (!isPaused.value) {
                    newClient.connect()
                }
            } else {
                existingClient.updateDevice(device)
            }
        }

        // 3. Atomically update the immutable state map, preserving DB query order
        devicesWithStateMap.update { currentMap ->
            val nextMap = LinkedHashMap<String, DeviceWithState>()
            for (device in newDeviceList) {
                val current = currentMap[device.macAddress]
                val currentClient = activeClients[device.macAddress]
                val currentStatus = currentClient?.status?.value ?: WebsocketStatus.DISCONNECTED

                nextMap[device.macAddress] = if (current != null) {
                    current.copy(
                        device = device,
                        websocketStatus = currentClient?.status?.value ?: current.websocketStatus,
                    )
                } else {
                    DeviceWithState(
                        device = device,
                        stateInfo = null,
                        websocketStatus = currentStatus,
                    )
                }
            }
            nextMap
        }
    }

    private fun startObservingClient(client: WebsocketClient) {
        val mac = client.device.macAddress
        val job = viewModelScope.launch {
            // Coroutine 1: Observe connection status
            launch {
                client.status.collect { status ->
                    onClientStatusChanged(mac, status)
                }
            }
            // Coroutine 2: Observe incoming state info frames
            launch {
                client.incomingStateInfo.collect { stateInfo ->
                    onIncomingStateInfo(mac, stateInfo)
                }
            }
        }
        clientJobs[mac] = job
    }

    private fun onClientStatusChanged(mac: String, status: WebsocketStatus) {
        Log.d(TAG, "Device $mac status changed to $status")
        devicesWithStateMap.update { currentMap ->
            val current = currentMap[mac] ?: return@update currentMap
            if (current.websocketStatus == status) return@update currentMap
            val nextMap = LinkedHashMap(currentMap)
            nextMap[mac] = current.copy(websocketStatus = status)
            nextMap
        }
    }

    private suspend fun onIncomingStateInfo(mac: String, stateInfo: DeviceStateInfo) {
        val currentSnapshot = devicesWithStateMap.value[mac]
        val currentDevice = currentSnapshot?.device
            ?: activeClients[mac]?.device
            ?: return

        // 1. Invoke domain use case to persist changes to Room DB if needed
        val updatedDevice = persistDeviceState(currentDevice, stateInfo, mac)
        if (updatedDevice != null) {
            activeClients[mac]?.updateDevice(updatedDevice)
        }

        // 2. Determine update tag outside the CAS block
        val currentBeforeCas = devicesWithStateMap.value[mac]
        val deviceToUse = if (updatedDevice != null) {
            (currentBeforeCas?.device ?: currentDevice).copy(
                originalName = updatedDevice.originalName,
                branch = updatedDevice.branch,
                repositoryId = updatedDevice.repositoryId,
                lastSeen = updatedDevice.lastSeen,
            )
        } else {
            currentBeforeCas?.device ?: currentDevice
        }

        val updateTag = determineUpdateTag(currentBeforeCas, deviceToUse, stateInfo, mac)

        // 3. Update reactive state map with new stateInfo and updated device metadata
        var updatedDeviceWithState: DeviceWithState? = null
        devicesWithStateMap.update { currentMap ->
            val current = currentMap[mac] ?: return@update currentMap
            val finalDevice = if (updatedDevice != null) {
                current.device.copy(
                    originalName = updatedDevice.originalName,
                    branch = updatedDevice.branch,
                    repositoryId = updatedDevice.repositoryId,
                    lastSeen = updatedDevice.lastSeen,
                )
            } else {
                current.device
            }

            val newDeviceWithState = current.copy(
                device = finalDevice,
                stateInfo = stateInfo,
                updateVersionTag = updateTag,
            )
            updatedDeviceWithState = newDeviceWithState
            val nextMap = LinkedHashMap(currentMap)
            nextMap[mac] = newDeviceWithState
            nextMap
        }

        // 4. Update Glance widgets with latest authoritative state
        updatedDeviceWithState?.let { state ->
            updateWidgetsSafe(state, mac)
        }
    }

    private suspend fun persistDeviceState(currentDevice: Device, stateInfo: DeviceStateInfo, mac: String): Device? =
        try {
            saveDeviceStateUseCase(currentDevice, stateInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist device state for $mac", e)
            null
        }

    private suspend fun determineUpdateTag(
        current: DeviceWithState?,
        deviceToUse: Device,
        stateInfo: DeviceStateInfo,
        mac: String,
    ): String? {
        val isInitialCheck = current?.stateInfo == null
        val versionChanged = current?.stateInfo?.info?.version != stateInfo.info.version
        val optionsChanged = current?.stateInfo?.info?.options != stateInfo.info.options
        val brandChanged = current?.stateInfo?.info?.brand != stateInfo.info.brand
        val productChanged = current?.stateInfo?.info?.product != stateInfo.info.product
        val repoChanged = current?.stateInfo?.info?.repository != stateInfo.info.repository
        val branchChanged = current?.device?.branch != deviceToUse.branch
        val skipTagChanged = current?.device?.skipUpdateTag != deviceToUse.skipUpdateTag

        val needsUpdateCheck = isInitialCheck ||
            versionChanged ||
            optionsChanged ||
            brandChanged ||
            productChanged ||
            repoChanged ||
            branchChanged ||
            skipTagChanged

        if (!needsUpdateCheck) {
            return current.updateVersionTag
        }

        return try {
            deviceUpdateManager.checkForUpdate(deviceToUse, stateInfo)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check update for $mac", e)
            current?.updateVersionTag
        }
    }

    private suspend fun updateWidgetsSafe(state: DeviceWithState, mac: String) {
        try {
            widgetManager.updateWidgetsFromDeviceWithState(applicationContext, state)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update widgets for $mac", e)
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
        activeClients.values.forEach { it.disconnect() }
    }

    /**
     * Resumes all active WebSocket connections.
     * Called when the app comes into the foreground.
     */
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.d(TAG, "onResume: App is in the foreground. Resuming all connections.")
        isPaused.value = false
        activeClients.values.forEach { it.connect() }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        } catch (e: Exception) {
            Log.w(TAG, "ProcessLifecycleOwner not available during onCleared: ${e.message}")
        }
        Log.d(TAG, "ViewModel cleared. Closing all WebSocket clients and cancelling jobs.")
        clientJobs.values.forEach { it.cancel() }
        clientJobs.clear()
        activeClients.values.forEach { it.destroy() }
        activeClients.clear()
    }

    /**
     * Attempts to reconnect to all offline devices.
     */
    fun refreshOfflineDevices() {
        Log.d(TAG, "Refreshing offline devices.")
        val offlineClients = activeClients.values.filter {
            it.status.value != WebsocketStatus.CONNECTED
        }
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
            val client = activeClients[device.device.macAddress]
            if (client == null) {
                Log.w(
                    TAG,
                    "setBrightness: No active client found for MAC address ${device.device.macAddress}",
                )
                return@launch
            }
            Log.d(TAG, "Setting brightness for ${device.device.macAddress} to $brightness")
            client.sendState(State(brightness = brightness))
        }
    }

    /**
     * Sets the power state for a specific device.
     *
     * @param device The device to update.
     * @param isOn The desired power state.
     */
    fun setDevicePower(device: DeviceWithState, isOn: Boolean) {
        viewModelScope.launch {
            val client = activeClients[device.device.macAddress]
            if (client == null) {
                Log.w(
                    TAG,
                    "setDevicePower: No active client found for MAC address ${device.device.macAddress}",
                )
                return@launch
            }
            Log.d(TAG, "Setting isOn for ${device.device.macAddress} to $isOn")
            client.sendState(State(isOn = isOn))
        }
    }

    /**
     * Deletes a device from the database and cleans up associated widgets.
     */
    fun deleteDevice(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Deleting device ${device.originalName} - ${device.address}")
            widgetManager.deleteWidgetsForDevice(applicationContext, device.macAddress)
            deviceRepository.delete(device)
        }
    }
}
