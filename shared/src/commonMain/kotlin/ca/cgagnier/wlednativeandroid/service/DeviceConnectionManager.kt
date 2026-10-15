package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceStore
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnection
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.shared.SynchronizedObject
import ca.cgagnier.wlednativeandroid.shared.currentTimeMillis
import ca.cgagnier.wlednativeandroid.shared.synchronized
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

private const val TAG = "DeviceConnectionManager"
private val logger = Logger.withTag(TAG)

data class DeviceConnectionManagerConfig(val backgroundPauseDelayMs: Long = 2_000L)

/**
 * Shared coordinator managing real-time connections, optimistic commands,
 * and state synchronization for WLED devices.
 */
@Suppress("LongParameterList")
class DeviceConnectionManager(
    private val store: DeviceStore,
    private val connectionFactory: DeviceConnectionFactory,
    private val connectivity: NetworkConnectivityManager,
    private val coroutineScope: CoroutineScope,
    private val timeProvider: () -> Long = ::currentTimeMillis,
    private val backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    @Suppress("UnusedPrivateProperty")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val config: DeviceConnectionManagerConfig = DeviceConnectionManagerConfig(),
) {

    private val _allDevicesWithState = MutableStateFlow<List<DeviceWithState>>(emptyList())
    val allDevicesWithState: StateFlow<List<DeviceWithState>> = _allDevicesWithState.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    private val lock = SynchronizedObject()
    private val activeConnections = mutableMapOf<String, DeviceConnection>()
    private val lastKnownAddresses = mutableMapOf<String, String>()
    private val connectionJobs = mutableMapOf<String, Job>()

    private var pauseJob: Job? = null

    init {
        coroutineScope.launch(backgroundDispatcher) {
            store.allDevices.collect { devices ->
                syncDevices(devices)
            }
        }

        coroutineScope.launch(backgroundDispatcher) {
            var wasConnected = connectivity.isConnected.value
            connectivity.isConnected.collect { isConnected ->
                if (isConnected && !wasConnected) {
                    onNetworkRecovered()
                }
                wasConnected = isConnected
            }
        }
    }

    private fun syncDevices(newDeviceList: List<Device>) {
        val newDeviceMap = newDeviceList.associateBy { it.macAddress }

        removeDeletedConnections(newDeviceMap.keys)
        createOrUpdateConnections(newDeviceMap)
        mergeDeviceListState(newDeviceList)
    }

    private fun removeDeletedConnections(newMacs: Set<String>) {
        val toDestroy = mutableListOf<DeviceConnection>()
        synchronized(lock) {
            val removedMacs = activeConnections.keys - newMacs
            for (mac in removedMacs) {
                connectionJobs.remove(mac)?.cancel()
                lastKnownAddresses.remove(mac)
                activeConnections.remove(mac)?.let { toDestroy.add(it) }
            }
        }
        toDestroy.forEach { it.destroy() }
    }

    private fun createOrUpdateConnections(newDeviceMap: Map<String, Device>) {
        val toConnect = mutableListOf<DeviceConnection>()
        synchronized(lock) {
            for ((mac, device) in newDeviceMap) {
                val existing = activeConnections[mac]
                val previousAddress = lastKnownAddresses[mac]
                if (existing == null) {
                    val newConn = connectionFactory.createConnection(device.address, coroutineScope)
                    activeConnections[mac] = newConn
                    lastKnownAddresses[mac] = device.address
                    startObservingConnection(mac, newConn)
                    if (!_isPaused.value) {
                        toConnect.add(newConn)
                    }
                } else if (previousAddress != null && previousAddress != device.address) {
                    logger.d { "Address changed for $mac to ${device.address}; reconnecting" }
                    connectionJobs.remove(mac)?.cancel()
                    existing.destroy()

                    val newConn = connectionFactory.createConnection(device.address, coroutineScope)
                    activeConnections[mac] = newConn
                    lastKnownAddresses[mac] = device.address
                    startObservingConnection(mac, newConn)
                    if (!_isPaused.value) {
                        toConnect.add(newConn)
                    }
                }
            }
        }
        toConnect.forEach { it.connect() }
    }

    private fun startObservingConnection(mac: String, connection: DeviceConnection) {
        val job = coroutineScope.launch(backgroundDispatcher) {
            supervisorScope {
                launch {
                    connection.status.collect { status ->
                        onConnectionStatusChanged(mac, status)
                    }
                }
                launch {
                    connection.incomingStateInfo.collect { stateInfo ->
                        try {
                            onIncomingStateInfo(mac, stateInfo)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                            logger.e(e) { "Error processing frame for $mac" }
                        }
                    }
                }
            }
        }
        synchronized(lock) {
            connectionJobs[mac]?.cancel()
            connectionJobs[mac] = job
        }
    }

    private fun onConnectionStatusChanged(mac: String, status: WebsocketStatus) {
        _allDevicesWithState.update { list ->
            list.map { current ->
                if (current.device.macAddress == mac) {
                    current.copy(websocketStatus = status)
                } else {
                    current
                }
            }
        }
    }

    private fun onIncomingStateInfo(mac: String, stateInfo: DeviceStateInfo) {
        val now = timeProvider()
        _allDevicesWithState.update { list ->
            list.map { current ->
                if (current.device.macAddress == mac) {
                    val updatedDevice = current.device.copy(lastSeen = now)
                    current.copy(
                        device = updatedDevice,
                        stateInfo = stateInfo,
                    )
                } else {
                    current
                }
            }
        }
    }

    private fun mergeDeviceListState(newDeviceList: List<Device>) {
        _allDevicesWithState.update { currentList ->
            val currentMap = currentList.associateBy { it.device.macAddress }
            newDeviceList.map { device ->
                val current = currentMap[device.macAddress]
                val connection = synchronized(lock) { activeConnections[device.macAddress] }
                val currentStatus = connection?.status?.value ?: WebsocketStatus.DISCONNECTED

                if (current != null) {
                    val inMemoryLastSeen = current.device.lastSeen
                    val effectiveLastSeen = if (inMemoryLastSeen > device.lastSeen) {
                        inMemoryLastSeen
                    } else {
                        device.lastSeen
                    }
                    val effectiveDevice = device.copy(lastSeen = effectiveLastSeen)

                    current.copy(
                        device = effectiveDevice,
                        websocketStatus = currentStatus,
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

    private fun onNetworkRecovered() {
        if (_isPaused.value) return
        val toReconnect = synchronized(lock) {
            activeConnections.values.filter { it.status.value == WebsocketStatus.DISCONNECTED }
        }
        toReconnect.forEach { it.connect() }
    }

    fun refreshOfflineDevices() {
        val offline = synchronized(lock) {
            activeConnections.values.filter { it.status.value == WebsocketStatus.DISCONNECTED }
        }
        offline.forEach { it.connect() }
    }

    fun onBackground() {
        synchronized(lock) {
            pauseJob?.cancel()
            pauseJob = coroutineScope.launch {
                delay(config.backgroundPauseDelayMs)
                _isPaused.value = true
                val connections = synchronized(lock) { activeConnections.values.toList() }
                connections.forEach { it.pause() }
            }
        }
    }

    fun onForeground() {
        synchronized(lock) {
            pauseJob?.cancel()
            pauseJob = null
            if (_isPaused.value) {
                _isPaused.value = false
                val connections = activeConnections.values.toList()
                connections.forEach { it.resume() }
            }
        }
    }

    suspend fun deleteDevice(device: Device) {
        val mac = device.macAddress
        val connection = synchronized(lock) {
            connectionJobs.remove(mac)?.cancel()
            lastKnownAddresses.remove(mac)
            activeConnections.remove(mac)
        }
        connection?.destroy()
        store.delete(device)
    }

    fun updateDeviceState(deviceWithState: DeviceWithState) {
        val mac = deviceWithState.device.macAddress
        _allDevicesWithState.update { list ->
            list.map { current ->
                if (current.device.macAddress == mac) {
                    deviceWithState
                } else {
                    current
                }
            }
        }
    }

    suspend fun setBrightness(macAddress: String, brightness: Int): Boolean =
        sendState(macAddress, State(brightness = brightness))

    suspend fun setPower(macAddress: String, isOn: Boolean): Boolean = sendState(macAddress, State(isOn = isOn))

    suspend fun sendState(macAddress: String, patch: State): Boolean {
        val connection = synchronized(lock) { activeConnections[macAddress] } ?: return false
        val current = _allDevicesWithState.value.firstOrNull { it.device.macAddress == macAddress }
            ?: return false
        val previousState = current.stateInfo?.state

        // Optimistic update
        if (current.stateInfo != null) {
            _allDevicesWithState.update { list ->
                list.map { item ->
                    if (item.device.macAddress == macAddress && item.stateInfo != null) {
                        val updatedState = applyPatch(item.stateInfo.state, patch)
                        item.copy(stateInfo = item.stateInfo.copy(state = updatedState))
                    } else {
                        item
                    }
                }
            }
        }

        val success = connection.sendState(patch)
        if (!success && previousState != null) {
            // Guarded rollback
            _allDevicesWithState.update { list ->
                list.map { item ->
                    if (item.device.macAddress == macAddress && item.stateInfo != null) {
                        val rolledBack = rollbackPatch(item.stateInfo.state, patch, previousState)
                        item.copy(stateInfo = item.stateInfo.copy(state = rolledBack))
                    } else {
                        item
                    }
                }
            }
        }
        return success
    }

    private fun applyPatch(original: State, patch: State): State = original.copy(
        isOn = patch.isOn ?: original.isOn,
        brightness = patch.brightness ?: original.brightness,
        transition = patch.transition ?: original.transition,
        selectedPresetId = patch.selectedPresetId ?: original.selectedPresetId,
        selectedPlaylistId = patch.selectedPlaylistId ?: original.selectedPlaylistId,
        nightlight = patch.nightlight ?: original.nightlight,
        liveDataOverride = patch.liveDataOverride ?: original.liveDataOverride,
        mainSegment = patch.mainSegment ?: original.mainSegment,
        segment = patch.segment ?: original.segment,
    )

    private fun <T> rollbackField(current: T?, patch: T?, previous: T?): T? =
        if (patch != null && current == patch) previous else current

    private fun rollbackPatch(current: State, patch: State, previous: State): State = current.copy(
        isOn = rollbackField(current.isOn, patch.isOn, previous.isOn),
        brightness = rollbackField(current.brightness, patch.brightness, previous.brightness),
        transition = rollbackField(current.transition, patch.transition, previous.transition),
        selectedPresetId = rollbackField(current.selectedPresetId, patch.selectedPresetId, previous.selectedPresetId),
        selectedPlaylistId = rollbackField(
            current.selectedPlaylistId,
            patch.selectedPlaylistId,
            previous.selectedPlaylistId,
        ),
        nightlight = rollbackField(current.nightlight, patch.nightlight, previous.nightlight),
        liveDataOverride = rollbackField(current.liveDataOverride, patch.liveDataOverride, previous.liveDataOverride),
        mainSegment = rollbackField(current.mainSegment, patch.mainSegment, previous.mainSegment),
        segment = rollbackField(current.segment, patch.segment, previous.segment),
    )

    fun destroy() {
        synchronized(lock) {
            pauseJob?.cancel()
            pauseJob = null
            connectionJobs.values.forEach { it.cancel() }
            connectionJobs.clear()
            activeConnections.values.forEach { it.destroy() }
            activeConnections.clear()
            lastKnownAddresses.clear()
        }
    }
}
