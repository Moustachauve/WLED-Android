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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    coroutineScope: CoroutineScope,
    private val timeProvider: () -> Long = ::currentTimeMillis,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    @Suppress("UnusedPrivateProperty")
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val config: DeviceConnectionManagerConfig = DeviceConnectionManagerConfig(),
) {
    private val managerScope = CoroutineScope(
        coroutineScope.coroutineContext +
            SupervisorJob(coroutineScope.coroutineContext[Job]) +
            backgroundDispatcher,
    )

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
        managerScope.launch {
            store.allDevices.collect { devices ->
                syncDevices(devices)
            }
        }

        managerScope.launch {
            var wasConnected = connectivity.isConnected.value
            connectivity.isConnected.collect { isConnected ->
                if (isConnected && !wasConnected) {
                    refreshOfflineDevices()
                }
                wasConnected = isConnected
            }
        }
    }

    private fun syncDevices(newDeviceList: List<Device>) {
        val newDeviceMap = newDeviceList.associateBy { it.macAddress }

        removeDeletedConnections(newDeviceMap.keys)
        val toConnect = createOrUpdateConnections(newDeviceMap)
        mergeDeviceListState(newDeviceList)
        toConnect.forEach { it.connect() }
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

    private fun createOrUpdateConnections(newDeviceMap: Map<String, Device>): List<DeviceConnection> {
        val toConnect = mutableListOf<DeviceConnection>()
        val toDestroy = mutableListOf<DeviceConnection>()
        val toObserve = mutableListOf<Pair<String, DeviceConnection>>()

        synchronized(lock) {
            for ((mac, device) in newDeviceMap) {
                val existing = activeConnections[mac]
                val previousAddress = lastKnownAddresses[mac]
                if (existing == null || previousAddress != device.address) {
                    if (existing != null) {
                        logger.d { "Address changed for $mac to ${device.address}; reconnecting" }
                        connectionJobs.remove(mac)?.cancel()
                        toDestroy.add(existing)
                    }

                    val newConn = connectionFactory.createConnection(device.address, managerScope)
                    activeConnections[mac] = newConn
                    lastKnownAddresses[mac] = device.address
                    toObserve.add(mac to newConn)
                    if (!_isPaused.value) {
                        toConnect.add(newConn)
                    }
                }
            }
        }

        toDestroy.forEach { it.destroy() }
        toObserve.forEach { (mac, conn) -> startObservingConnection(mac, conn) }
        return toConnect
    }

    private fun startObservingConnection(mac: String, connection: DeviceConnection) {
        val job = managerScope.launch {
            launch {
                connection.status.collect { status ->
                    onConnectionStatusChanged(mac, connection, status)
                }
            }
            launch {
                connection.incomingStateInfo.collect { stateInfo ->
                    try {
                        onIncomingStateInfo(mac, connection, stateInfo)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        logger.e(e) { "Error processing frame for $mac" }
                    }
                }
            }
        }
        synchronized(lock) {
            if (activeConnections[mac] === connection) {
                connectionJobs[mac]?.cancel()
                connectionJobs[mac] = job
            } else {
                job.cancel()
            }
        }
    }

    private inline fun updateDevice(mac: String, crossinline transform: (DeviceWithState) -> DeviceWithState) {
        _allDevicesWithState.update { list ->
            list.map { if (it.device.macAddress == mac) transform(it) else it }
        }
    }

    private fun onConnectionStatusChanged(mac: String, connection: DeviceConnection, status: WebsocketStatus) {
        if (synchronized(lock) { activeConnections[mac] !== connection }) return
        updateDevice(mac) { it.copy(websocketStatus = status) }
    }

    private fun onIncomingStateInfo(mac: String, connection: DeviceConnection, stateInfo: DeviceStateInfo) {
        if (synchronized(lock) { activeConnections[mac] !== connection }) return
        val now = timeProvider()
        updateDevice(mac) { it.copy(device = it.device.copy(lastSeen = now), stateInfo = stateInfo) }
    }

    private fun mergeDeviceListState(newDeviceList: List<Device>) {
        val statusByMac = synchronized(lock) {
            activeConnections.mapValues { (_, conn) -> conn.status.value }
        }
        _allDevicesWithState.update { currentList ->
            val currentMap = currentList.associateBy { it.device.macAddress }
            newDeviceList.map { device ->
                val current = currentMap[device.macAddress]
                val currentStatus = statusByMac[device.macAddress] ?: WebsocketStatus.DISCONNECTED

                current?.copy(
                    device = device.copy(lastSeen = maxOf(current.device.lastSeen, device.lastSeen)),
                    websocketStatus = currentStatus,
                ) ?: DeviceWithState(
                    device = device,
                    stateInfo = null,
                    websocketStatus = currentStatus,
                )
            }
        }
    }

    fun refreshOfflineDevices() {
        if (_isPaused.value) return
        val offline = synchronized(lock) {
            activeConnections.values.filter { it.status.value == WebsocketStatus.DISCONNECTED }
        }
        offline.forEach { it.connect() }
    }

    fun onBackground() {
        synchronized(lock) {
            pauseJob?.cancel()
            pauseJob = managerScope.launch {
                delay(config.backgroundPauseDelayMs)
                val connections = synchronized(lock) {
                    if (!isActive) return@launch
                    _isPaused.value = true
                    activeConnections.values.toList()
                }
                connections.forEach { it.pause() }
            }
        }
    }

    fun onForeground() {
        val connectionsToResume = synchronized(lock) {
            pauseJob?.cancel()
            pauseJob = null
            if (_isPaused.value) {
                _isPaused.value = false
                activeConnections.values.toList()
            } else {
                emptyList()
            }
        }
        connectionsToResume.forEach { it.resume() }
    }

    suspend fun deleteDevice(device: Device) {
        store.delete(device)
    }

    fun updateDeviceState(deviceWithState: DeviceWithState) {
        updateDevice(deviceWithState.device.macAddress) { deviceWithState }
    }

    suspend fun setBrightness(macAddress: String, brightness: Int): Boolean =
        sendState(macAddress, State(brightness = brightness))

    suspend fun setPower(macAddress: String, isOn: Boolean): Boolean = sendState(macAddress, State(isOn = isOn))

    suspend fun sendState(macAddress: String, patch: State): Boolean {
        val connection = synchronized(lock) { activeConnections[macAddress] } ?: return false

        val previousList = _allDevicesWithState.getAndUpdate { list ->
            list.map { item ->
                if (item.device.macAddress == macAddress && item.stateInfo != null) {
                    item.copy(stateInfo = item.stateInfo.copy(state = applyPatch(item.stateInfo.state, patch)))
                } else {
                    item
                }
            }
        }
        val previousItem = previousList.firstOrNull { it.device.macAddress == macAddress } ?: return false
        val previousState = previousItem.stateInfo?.state

        val success = connection.sendState(patch)
        if (!success && previousState != null) {
            updateDevice(macAddress) { item ->
                item.stateInfo?.let { info ->
                    item.copy(stateInfo = info.copy(state = rollbackPatch(info.state, patch, previousState)))
                } ?: item
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
        managerScope.cancel()
        val toDestroy = synchronized(lock) {
            pauseJob = null
            connectionJobs.clear()
            val connections = activeConnections.values.toList()
            activeConnections.clear()
            lastKnownAddresses.clear()
            connections
        }
        toDestroy.forEach { it.destroy() }
    }
}
