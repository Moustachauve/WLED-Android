package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceStore
import ca.cgagnier.wlednativeandroid.service.update.RepositoryIdResolver
import ca.cgagnier.wlednativeandroid.service.update.UpdateTagResolver
import ca.cgagnier.wlednativeandroid.service.update.getRepositoryFromInfo
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

data class DeviceConnectionManagerConfig(
    val backgroundPauseDelayMs: Long = 2_000L,
    val lastSeenPersistIntervalMs: Long = 30_000L,
    val updateCheckCacheTtlMs: Long = 3_600_000L, // 1 hour
    val updateCheckRetryIntervalMs: Long = 60_000L, // 1 minute
)

/**
 * Shared coordinator managing real-time connections, optimistic commands,
 * targeted column persistence, and software update checks for WLED devices.
 */
@Suppress("LongParameterList")
class DeviceConnectionManager(
    private val store: DeviceStore,
    private val connectionFactory: DeviceConnectionFactory,
    private val connectivity: NetworkConnectivityManager,
    private val updateTagResolver: UpdateTagResolver,
    private val repositoryIdResolver: RepositoryIdResolver,
    coroutineScope: CoroutineScope,
    private val timeProvider: () -> Long = ::currentTimeMillis,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
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
    private val updateCheckJobs = mutableMapOf<String, Job>()
    private val lastSeenPersisted = mutableMapOf<String, Long>()
    private val lastSuccessfulUpdateCheck = mutableMapOf<String, Long>()
    private val lastUpdateCheckAttempt = mutableMapOf<String, Long>()

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

    private suspend fun syncDevices(newDeviceList: List<Device>) {
        val newDeviceMap = newDeviceList.associateBy { it.macAddress }
        val previousStateMap = _allDevicesWithState.value.associateBy { it.device.macAddress }

        removeDeletedConnections(newDeviceMap.keys)
        val toConnect = createOrUpdateConnections(newDeviceMap)
        mergeDeviceListState(newDeviceList)
        toConnect.forEach { it.connect() }
        recheckModifiedDevicesAsync(newDeviceList, previousStateMap)
    }

    private fun removeDeletedConnections(newMacs: Set<String>) {
        val toDestroy = mutableListOf<DeviceConnection>()
        synchronized(lock) {
            val removedMacs = activeConnections.keys - newMacs
            for (mac in removedMacs) {
                connectionJobs.remove(mac)?.cancel()
                updateCheckJobs.remove(mac)?.cancel()
                lastKnownAddresses.remove(mac)
                lastSeenPersisted.remove(mac)
                lastSuccessfulUpdateCheck.remove(mac)
                lastUpdateCheckAttempt.remove(mac)
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

    private fun mergeDeviceListState(newDeviceList: List<Device>) {
        val statusByMac = synchronized(lock) {
            activeConnections.mapValues { (_, conn) -> conn.status.value }
        }
        _allDevicesWithState.update { currentList ->
            val currentMap = currentList.associateBy { it.device.macAddress }
            newDeviceList.map { device ->
                val current = currentMap[device.macAddress]
                val currentStatus = statusByMac[device.macAddress] ?: WebsocketStatus.DISCONNECTED

                if (current != null) {
                    val effectiveLastSeen = maxOf(current.device.lastSeen, device.lastSeen)
                    val effectiveDevice = device.copy(lastSeen = effectiveLastSeen)
                    val updateTag = if (effectiveDevice.skipUpdateTag.isNotEmpty() &&
                        effectiveDevice.skipUpdateTag == current.updateVersionTag
                    ) {
                        null
                    } else {
                        current.updateVersionTag
                    }

                    current.copy(
                        device = effectiveDevice,
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

    private fun recheckModifiedDevicesAsync(
        newDeviceList: List<Device>,
        previousStateMap: Map<String, DeviceWithState>,
    ) {
        for (device in newDeviceList) {
            val previous = previousStateMap[device.macAddress]
            val stateInfo = previous?.stateInfo
            if (previous == null || stateInfo == null) {
                continue
            }
            val mac = device.macAddress

            val branchChanged = device.branch != previous.device.branch
            val unskipped = device.skipUpdateTag.isEmpty() && previous.device.skipUpdateTag.isNotEmpty()
            if (branchChanged || unskipped) {
                scheduleModifiedUpdateCheck(mac, device, previous, stateInfo)
            }
        }
    }

    private fun scheduleModifiedUpdateCheck(
        mac: String,
        device: Device,
        previous: DeviceWithState,
        stateInfo: DeviceStateInfo,
    ) {
        synchronized(lock) {
            lastSuccessfulUpdateCheck.remove(mac)
            lastUpdateCheckAttempt.remove(mac)
            updateCheckJobs.remove(mac)?.cancel()
            val job = managerScope.launch {
                val newTag = determineUpdateTag(
                    current = previous,
                    deviceToUse = device,
                    stateInfo = stateInfo,
                    mac = mac,
                    now = timeProvider(),
                )
                val tag = if (device.skipUpdateTag.isNotEmpty() && device.skipUpdateTag == newTag) {
                    null
                } else {
                    newTag
                }
                updateDevice(mac) { current ->
                    current.copy(updateVersionTag = tag)
                }
            }
            updateCheckJobs[mac] = job
        }
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

    private suspend fun onIncomingStateInfo(mac: String, connection: DeviceConnection, stateInfo: DeviceStateInfo) {
        if (synchronized(lock) { activeConnections[mac] !== connection }) return
        val now = timeProvider()
        val current = _allDevicesWithState.value.firstOrNull { it.device.macAddress == mac } ?: return
        val currentDevice = current.device

        // 1. Targeted column writes if metadata changed
        val name = stateInfo.info.name
        val branch = inferBranch(currentDevice.branch, stateInfo.info.version)
        val repoStr = getRepositoryFromInfo(stateInfo.info)
        val repoId = resolveRepositoryIdSafe(repoStr, currentDevice.repositoryId)
        persistMetadataChanges(mac, currentDevice, name, branch, repoId)

        // 2. Throttled lastSeen persistence
        persistThrottledLastSeen(mac, now)

        // 3. Determine software update tag
        val updatedDevice = currentDevice.copy(
            originalName = if (name.isNotBlank()) name else currentDevice.originalName,
            branch = branch,
            repositoryId = repoId,
            lastSeen = now,
        )
        val updateTag = determineUpdateTag(current, updatedDevice, stateInfo, mac, now)

        // 4. Update in-memory state
        updateInMemoryState(mac, updatedDevice, stateInfo, updateTag)
    }

    private suspend fun resolveRepositoryIdSafe(repoStr: String, fallback: Long): Long = try {
        repositoryIdResolver.getOrCreateRepositoryId(repoStr)
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.e(e) { "Failed to resolve repository ID for $repoStr" }
        fallback
    }

    private suspend fun persistMetadataChanges(
        mac: String,
        currentDevice: Device,
        name: String,
        branch: Branch,
        repoId: Long,
    ) {
        if (name.isNotBlank() && name != currentDevice.originalName) {
            try {
                store.updateOriginalName(mac, name)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.e(e) { "Failed to update originalName for $mac" }
            }
        }

        if (branch != currentDevice.branch) {
            try {
                store.updateBranch(mac, branch)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.e(e) { "Failed to update branch for $mac" }
            }
        }

        if (repoId != currentDevice.repositoryId) {
            try {
                store.updateRepositoryId(mac, repoId)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.e(e) { "Failed to update repositoryId for $mac" }
            }
        }
    }

    private suspend fun persistThrottledLastSeen(mac: String, now: Long) {
        val lastPersisted = synchronized(lock) { lastSeenPersisted[mac] ?: 0L }
        if (now - lastPersisted >= config.lastSeenPersistIntervalMs) {
            synchronized(lock) { lastSeenPersisted[mac] = now }
            try {
                store.updateLastSeen(mac, now)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.e(e) { "Failed to update lastSeen for $mac" }
            }
        }
    }

    private fun updateInMemoryState(
        mac: String,
        updatedDevice: Device,
        stateInfo: DeviceStateInfo,
        updateTag: String?,
    ) {
        val finalTag = if (updatedDevice.skipUpdateTag.isNotEmpty() &&
            updatedDevice.skipUpdateTag == updateTag
        ) {
            null
        } else {
            updateTag
        }
        updateDevice(mac) { item ->
            item.copy(
                device = updatedDevice,
                stateInfo = stateInfo,
                updateVersionTag = finalTag,
            )
        }
    }

    private suspend fun determineUpdateTag(
        current: DeviceWithState,
        deviceToUse: Device,
        stateInfo: DeviceStateInfo,
        mac: String,
        now: Long,
    ): String? {
        val metadataChanged = hasMetadataChanged(current, deviceToUse, stateInfo)
        if (metadataChanged) {
            synchronized(lock) {
                lastSuccessfulUpdateCheck.remove(mac)
            }
        }

        val lastSuccessfulCheck = synchronized(lock) { lastSuccessfulUpdateCheck[mac] }
        val hasRecentCheck = lastSuccessfulCheck != null &&
            now - lastSuccessfulCheck < config.updateCheckCacheTtlMs
        val lastAttempt = synchronized(lock) { lastUpdateCheckAttempt[mac] ?: 0L }
        val retryAllowed = now - lastAttempt > config.updateCheckRetryIntervalMs

        if (!metadataChanged && (hasRecentCheck || !retryAllowed)) {
            return current.updateVersionTag
        }

        synchronized(lock) { lastUpdateCheckAttempt[mac] = now }
        return try {
            val tag = updateTagResolver.checkForUpdate(deviceToUse, stateInfo)
            synchronized(lock) { lastSuccessfulUpdateCheck[mac] = now }
            tag
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            logger.e(e) { "Failed to check update for $mac" }
            current.updateVersionTag
        }
    }

    private fun hasMetadataChanged(
        current: DeviceWithState,
        deviceToUse: Device,
        stateInfo: DeviceStateInfo,
    ): Boolean {
        val oldStateInfo = current.stateInfo ?: return true
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

    private fun inferBranch(currentBranch: Branch, version: String?): Branch {
        if (currentBranch != Branch.UNKNOWN || version.isNullOrBlank()) {
            return currentBranch
        }
        return if (version.contains("-b")) Branch.BETA else Branch.STABLE
    }

    fun refreshOfflineDevices() {
        if (_isPaused.value) return
        val offline = synchronized(lock) {
            activeConnections.values.filter { it.status.value == WebsocketStatus.DISCONNECTED }
        }
        offline.forEach { it.connect() }
    }

    fun onBackground() {
        managerScope.launch(ioDispatcher) {
            flushLastSeen()
        }
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

    suspend fun flushLastSeen() {
        val toPersist: List<Pair<String, Long>> = _allDevicesWithState.value.mapNotNull { item ->
            val mac = item.device.macAddress
            val currentLastSeen = item.device.lastSeen
            val persisted = synchronized(lock) { lastSeenPersisted[mac] ?: 0L }
            if (currentLastSeen > persisted) {
                synchronized(lock) { lastSeenPersisted[mac] = currentLastSeen }
                mac to currentLastSeen
            } else {
                null
            }
        }
        for ((mac, lastSeen) in toPersist) {
            try {
                store.updateLastSeen(mac, lastSeen)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                logger.e(e) { "Failed to flush lastSeen for $mac" }
            }
        }
    }

    suspend fun deleteDevice(device: Device) {
        val mac = device.macAddress
        val connection = synchronized(lock) {
            connectionJobs.remove(mac)?.cancel()
            updateCheckJobs.remove(mac)?.cancel()
            lastKnownAddresses.remove(mac)
            lastSeenPersisted.remove(mac)
            lastSuccessfulUpdateCheck.remove(mac)
            lastUpdateCheckAttempt.remove(mac)
            activeConnections.remove(mac)
        }
        connection?.destroy()
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
            updateCheckJobs.clear()
            lastSeenPersisted.clear()
            lastSuccessfulUpdateCheck.clear()
            lastUpdateCheckAttempt.clear()
            val connections = activeConnections.values.toList()
            activeConnections.clear()
            lastKnownAddresses.clear()
            connections
        }
        toDestroy.forEach { it.destroy() }
    }
}
