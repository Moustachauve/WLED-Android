package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.di.DefaultDispatcher
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @DefaultDispatcher private val backgroundDispatcher: CoroutineDispatcher,
) : ViewModel(),
    DefaultLifecycleObserver {

    private val activeClients = ConcurrentHashMap<String, WebsocketClient>()
    private val clientJobs = ConcurrentHashMap<String, Job>()

    private val _allDevicesWithState = MutableStateFlow<List<DeviceWithState>>(emptyList())
    val allDevicesWithState: StateFlow<List<DeviceWithState>> = _allDevicesWithState.asStateFlow()

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

    private suspend fun syncDevices(newDeviceList: List<Device>) {
        val newDeviceMap = newDeviceList.associateBy { it.macAddress }
        removeStaleClients(newDeviceMap)
        createOrUpdateClients(newDeviceMap)

        val previousStateMap = _allDevicesWithState.value.associateBy { it.device.macAddress }
        updateDevicesWithStateList(newDeviceList)
        recheckModifiedDeviceUpdates(newDeviceList, previousStateMap)
    }

    private fun removeStaleClients(newDeviceMap: Map<String, Device>) {
        val devicesToRemove = activeClients.keys - newDeviceMap.keys
        for (macAddress in devicesToRemove) {
            Log.d(TAG, "[Sync] Device removed: $macAddress. Cancelling job and destroying client.")
            clientJobs.remove(macAddress)?.cancel()
            activeClients.remove(macAddress)?.destroy()
        }
    }

    private fun createOrUpdateClients(newDeviceMap: Map<String, Device>) {
        for ((macAddress, device) in newDeviceMap) {
            val existingClient = activeClients[macAddress]
            if (existingClient == null) {
                Log.d(TAG, "[Sync] Device added: $macAddress (${device.address}). Creating client.")
                val newClient = websocketClientFactory.create(device, coroutineScope = viewModelScope)
                activeClients[macAddress] = newClient
                startObservingClient(newClient)
                if (!isPaused.value) {
                    newClient.connect()
                }
            } else if (existingClient.device.address != device.address) {
                Log.d(
                    TAG,
                    "[Sync] Device address changed for $macAddress to ${device.address}. Reconnecting client.",
                )
                clientJobs.remove(macAddress)?.cancel()
                existingClient.destroy()

                val newClient = websocketClientFactory.create(device, coroutineScope = viewModelScope)
                activeClients[macAddress] = newClient
                startObservingClient(newClient)
                if (!isPaused.value) {
                    newClient.connect()
                }
            } else {
                existingClient.updateDevice(device)
            }
        }
    }

    private fun updateDevicesWithStateList(newDeviceList: List<Device>) {
        _allDevicesWithState.update { currentList ->
            val currentMap = currentList.associateBy { it.device.macAddress }
            newDeviceList.map { device ->
                val current = currentMap[device.macAddress]
                val currentClient = activeClients[device.macAddress]
                val currentStatus = currentClient?.status?.value ?: WebsocketStatus.DISCONNECTED

                if (current != null) {
                    val updateTag = if (device.skipUpdateTag.isNotEmpty() &&
                        device.skipUpdateTag == current.updateVersionTag
                    ) {
                        null
                    } else {
                        current.updateVersionTag
                    }
                    current.copy(
                        device = device,
                        websocketStatus = currentStatus,
                        updateVersionTag = updateTag,
                    )
                } else {
                    DeviceWithState(
                        device = device,
                        stateInfo = null,
                        websocketStatus = currentStatus,
                    )
                }
            }
        }
    }

    private suspend fun recheckModifiedDeviceUpdates(
        newDeviceList: List<Device>,
        previousStateMap: Map<String, DeviceWithState>,
    ) {
        for (device in newDeviceList) {
            val previous = previousStateMap[device.macAddress]
            val stateInfo = previous?.stateInfo
            if (previous != null && stateInfo != null && shouldRecheckDeviceUpdate(device, previous)) {
                val current = _allDevicesWithState.value.firstOrNull { it.device.macAddress == device.macAddress }
                val newTag = determineUpdateTag(current, device, stateInfo, device.macAddress)
                if (newTag != current?.updateVersionTag) {
                    _allDevicesWithState.update { list ->
                        list.map {
                            if (it.device.macAddress == device.macAddress) {
                                it.copy(updateVersionTag = newTag)
                            } else {
                                it
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shouldRecheckDeviceUpdate(device: Device, previous: DeviceWithState): Boolean {
        val branchChanged = device.branch != previous.device.branch
        val unskippedTag = device.skipUpdateTag != previous.device.skipUpdateTag && device.skipUpdateTag.isEmpty()
        return branchChanged || unskippedTag
    }

    private fun startObservingClient(client: WebsocketClient) {
        val mac = client.device.macAddress
        val job = viewModelScope.launch(backgroundDispatcher + SupervisorJob()) {
            // Coroutine 1: Observe connection status
            launch {
                client.status.collect { status ->
                    onClientStatusChanged(mac, status)
                }
            }
            // Coroutine 2: Observe incoming state info frames
            launch {
                client.incomingStateInfo.collect { stateInfo ->
                    try {
                        onIncomingStateInfo(mac, stateInfo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing incoming frame for $mac", e)
                    }
                }
            }
        }
        clientJobs[mac] = job
    }

    private fun onClientStatusChanged(mac: String, status: WebsocketStatus) {
        Log.d(TAG, "Device $mac status changed to $status")
        _allDevicesWithState.update { currentList ->
            var changed = false
            val nextList = currentList.map { item ->
                if (item.device.macAddress == mac && item.websocketStatus != status) {
                    changed = true
                    item.copy(websocketStatus = status)
                } else {
                    item
                }
            }
            if (changed) nextList else currentList
        }
    }

    private suspend fun onIncomingStateInfo(mac: String, stateInfo: DeviceStateInfo) {
        val currentSnapshot = _allDevicesWithState.value.firstOrNull { it.device.macAddress == mac }
        val currentDevice = currentSnapshot?.device
            ?: activeClients[mac]?.device
            ?: return

        // 1. Invoke domain use case to persist changes to Room DB if needed
        val updatedDevice = persistDeviceState(currentDevice, stateInfo, mac)
        if (updatedDevice != null) {
            activeClients[mac]?.updateDevice(updatedDevice)
        }

        // 2. Determine update tag outside the CAS block
        val currentBeforeCas = _allDevicesWithState.value.firstOrNull { it.device.macAddress == mac }
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

        // 3. Update reactive state list with new stateInfo and updated device metadata
        updateStateWithIncomingStateInfo(mac, deviceToUse, updatedDevice, stateInfo, updateTag)

        // 4. Update Glance widgets with latest authoritative state
        val updatedDeviceWithState = _allDevicesWithState.value.firstOrNull { it.device.macAddress == mac }
        if (updatedDeviceWithState != null) {
            updateWidgetsSafe(updatedDeviceWithState, mac)
        }
    }

    private fun updateStateWithIncomingStateInfo(
        mac: String,
        deviceToUse: Device,
        updatedDevice: Device?,
        stateInfo: DeviceStateInfo,
        updateTag: String?,
    ) {
        _allDevicesWithState.update { currentList ->
            var changed = false
            val nextList = currentList.map { current ->
                if (current.device.macAddress == mac) {
                    changed = true
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

                    current.copy(
                        device = finalDevice,
                        stateInfo = stateInfo,
                        updateVersionTag = updateTag,
                    )
                } else {
                    current
                }
            }
            if (changed) {
                nextList
            } else {
                currentList + DeviceWithState(
                    device = deviceToUse,
                    stateInfo = stateInfo,
                    websocketStatus = activeClients[mac]?.status?.value ?: WebsocketStatus.CONNECTED,
                    updateVersionTag = updateTag,
                )
            }
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
            withContext(Dispatchers.IO) {
                widgetManager.updateWidgetsFromDeviceWithState(applicationContext, state)
            }
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
            it.status.value == WebsocketStatus.DISCONNECTED
        }
        offlineClients.forEach {
            it.connect()
        }
    }

    /**
     * Optimistically updates the in-memory state of a device (e.g. immediately after OTA install).
     */
    fun updateDeviceState(updatedDeviceWithState: DeviceWithState) {
        _allDevicesWithState.update { currentList ->
            currentList.map { current ->
                if (current.device.macAddress == updatedDeviceWithState.device.macAddress) {
                    updatedDeviceWithState
                } else {
                    current
                }
            }
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
