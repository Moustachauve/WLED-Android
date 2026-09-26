package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.di.DefaultDispatcher
import ca.cgagnier.wlednativeandroid.di.IoDispatcher
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

private const val TAG = "DeviceWebsocketListViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5000L
private const val UPDATE_CHECK_RETRY_INTERVAL_MS = 60_000L

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
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel(),
    DefaultLifecycleObserver {

    internal var currentTimeProvider: () -> Long = System::currentTimeMillis
    internal var lifecycleOwner: LifecycleOwner? = null

    constructor(
        userPreferencesRepository: UserPreferencesRepository,
        deviceRepository: DeviceRepository,
        websocketClientFactory: WebsocketClientFactory,
        widgetManager: WledWidgetManager,
        saveDeviceStateUseCase: SaveDeviceStateUseCase,
        deviceUpdateManager: DeviceUpdateManager,
        applicationContext: Context,
        backgroundDispatcher: CoroutineDispatcher,
        ioDispatcher: CoroutineDispatcher = backgroundDispatcher,
        currentTimeProvider: () -> Long = System::currentTimeMillis,
        lifecycleOwner: LifecycleOwner? = null,
    ) : this(
        userPreferencesRepository = userPreferencesRepository,
        deviceRepository = deviceRepository,
        websocketClientFactory = websocketClientFactory,
        widgetManager = widgetManager,
        saveDeviceStateUseCase = saveDeviceStateUseCase,
        deviceUpdateManager = deviceUpdateManager,
        applicationContext = applicationContext,
        backgroundDispatcher = backgroundDispatcher,
        ioDispatcher = ioDispatcher,
    ) {
        this.currentTimeProvider = currentTimeProvider
        this.lifecycleOwner = lifecycleOwner
    }

    private val activeClients = ConcurrentHashMap<String, WebsocketClient>()
    private val clientJobs = ConcurrentHashMap<String, Job>()
    private val devicesWithCompletedUpdateCheck = ConcurrentHashMap.newKeySet<String>()
    private val lastUpdateCheckAttempt = ConcurrentHashMap<String, Long>()

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
        try {
            val owner = lifecycleOwner ?: ProcessLifecycleOwner.get()
            owner.lifecycle.addObserver(this)
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
        val tagsToUpdate = mutableMapOf<String, String?>()

        for (device in newDeviceList) {
            val previous = previousStateMap[device.macAddress]
            val stateInfo = previous?.stateInfo
            if (previous != null && stateInfo != null && shouldRecheckDeviceUpdate(device, previous)) {
                devicesWithCompletedUpdateCheck.remove(device.macAddress)
                lastUpdateCheckAttempt.remove(device.macAddress)
                val newTag = determineUpdateTag(
                    current = previous,
                    deviceToUse = device,
                    stateInfo = stateInfo,
                    mac = device.macAddress,
                )
                tagsToUpdate[device.macAddress] = newTag
            }
        }

        _allDevicesWithState.update { currentList ->
            val currentMap = currentList.associateBy { it.device.macAddress }
            newDeviceList.map { device ->
                val current = currentMap[device.macAddress]
                val currentClient = activeClients[device.macAddress]
                val currentStatus = currentClient?.status?.value ?: WebsocketStatus.DISCONNECTED

                if (current != null) {
                    val updateTag = if (tagsToUpdate.containsKey(device.macAddress)) {
                        tagsToUpdate[device.macAddress]
                    } else if (device.skipUpdateTag.isNotEmpty() &&
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

    private fun removeStaleClients(newDeviceMap: Map<String, Device>) {
        val devicesToRemove = activeClients.keys - newDeviceMap.keys
        for (macAddress in devicesToRemove) {
            Log.d(TAG, "[Sync] Device removed: $macAddress. Cancelling job and destroying client.")
            clientJobs.remove(macAddress)?.cancel()
            activeClients.remove(macAddress)?.destroy()
            devicesWithCompletedUpdateCheck.remove(macAddress)
            lastUpdateCheckAttempt.remove(macAddress)
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

    private fun shouldRecheckDeviceUpdate(device: Device, previous: DeviceWithState): Boolean {
        val branchChanged = device.branch != previous.device.branch
        val unskippedTag = device.skipUpdateTag != previous.device.skipUpdateTag && device.skipUpdateTag.isEmpty()
        return branchChanged || unskippedTag
    }

    private fun startObservingClient(client: WebsocketClient) {
        val mac = client.device.macAddress
        val job = viewModelScope.launch(backgroundDispatcher) {
            supervisorScope {
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

        val (previousForWidget, updatedForWidget) =
            updateStateFromFrame(mac, updatedDevice, updateTag, stateInfo)

        if (updatedForWidget != null && hasWidgetVisibleChanges(previousForWidget, updatedForWidget)) {
            updateWidgetsSafe(updatedForWidget, mac)
        }
    }

    private fun updateStateFromFrame(
        mac: String,
        updatedDevice: Device?,
        updateTag: String?,
        stateInfo: DeviceStateInfo,
    ): Pair<DeviceWithState?, DeviceWithState?> {
        var previousForWidget: DeviceWithState? = null
        var updatedForWidget: DeviceWithState? = null

        _allDevicesWithState.update { currentList ->
            var changed = false
            val nextList = currentList.map { current ->
                if (current.device.macAddress == mac) {
                    changed = true
                    previousForWidget = current
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

                    val finalTag = if (finalDevice.skipUpdateTag.isNotEmpty() &&
                        finalDevice.skipUpdateTag == updateTag
                    ) {
                        null
                    } else {
                        updateTag ?: current.updateVersionTag
                    }

                    val updated = current.copy(
                        device = finalDevice,
                        stateInfo = stateInfo,
                        updateVersionTag = finalTag,
                    )
                    updatedForWidget = updated
                    updated
                } else {
                    current
                }
            }
            if (changed) nextList else currentList
        }

        return Pair(previousForWidget, updatedForWidget)
    }

    private fun hasWidgetVisibleChanges(previous: DeviceWithState?, next: DeviceWithState): Boolean {
        if (previous == null || previous.stateInfo == null) return true
        val prevInfo = previous.stateInfo
        val nextInfo = next.stateInfo ?: return false
        val prevDevice = previous.device
        val nextDevice = next.device

        return prevInfo.state.isOn != nextInfo.state.isOn ||
            prevInfo.state.brightness != nextInfo.state.brightness ||
            prevInfo.state.segment != nextInfo.state.segment ||
            prevDevice.customName != nextDevice.customName ||
            prevDevice.originalName != nextDevice.originalName ||
            prevDevice.address != nextDevice.address
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

    private fun hasMetadataChanged(
        current: DeviceWithState?,
        deviceToUse: Device,
        stateInfo: DeviceStateInfo,
    ): Boolean {
        val oldStateInfo = current?.stateInfo ?: return true
        val info = stateInfo.info
        val oldInfo = oldStateInfo.info

        val firmwareChanged = oldInfo.version != info.version ||
            oldInfo.options != info.options ||
            oldInfo.repository != info.repository
        val hardwareChanged = oldInfo.brand != info.brand ||
            oldInfo.product != info.product
        val settingsChanged = current.device.branch != deviceToUse.branch ||
            current.device.skipUpdateTag != deviceToUse.skipUpdateTag

        return firmwareChanged || hardwareChanged || settingsChanged
    }

    private suspend fun determineUpdateTag(
        current: DeviceWithState?,
        deviceToUse: Device,
        stateInfo: DeviceStateInfo,
        mac: String,
        currentTimeMillis: Long = currentTimeProvider(),
    ): String? {
        val metadataChanged = hasMetadataChanged(current, deviceToUse, stateInfo)
        if (metadataChanged) {
            devicesWithCompletedUpdateCheck.remove(mac)
        }

        val hasChecked = devicesWithCompletedUpdateCheck.contains(mac)
        val lastAttempt = lastUpdateCheckAttempt[mac] ?: 0L
        val retryAllowed = currentTimeMillis - lastAttempt > UPDATE_CHECK_RETRY_INTERVAL_MS

        if (hasChecked || (!metadataChanged && !retryAllowed)) {
            return current?.updateVersionTag
        }

        lastUpdateCheckAttempt[mac] = currentTimeMillis
        return try {
            val tag = deviceUpdateManager.checkForUpdate(deviceToUse, stateInfo)
            devicesWithCompletedUpdateCheck.add(mac)
            tag
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check update for $mac", e)
            current?.updateVersionTag
        }
    }

    private suspend fun updateWidgetsSafe(state: DeviceWithState, mac: String) {
        try {
            withContext(ioDispatcher) {
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
            val owner = lifecycleOwner ?: ProcessLifecycleOwner.get()
            owner.lifecycle.removeObserver(this)
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
        viewModelScope.launch(ioDispatcher) {
            Log.d(TAG, "Deleting device ${device.originalName} - ${device.address}")
            widgetManager.deleteWidgetsForDevice(applicationContext, device.macAddress)
            deviceRepository.delete(device)
        }
    }
}
