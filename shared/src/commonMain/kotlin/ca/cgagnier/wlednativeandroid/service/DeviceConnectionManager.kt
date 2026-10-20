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

const val DEFAULT_BACKGROUND_PAUSE_DELAY_MS = 2_000L
const val DEFAULT_LAST_SEEN_PERSIST_INTERVAL_MS = 30_000L
const val DEFAULT_UPDATE_CHECK_CACHE_TTL_MS = 3_600_000L // 1 hour
const val DEFAULT_UPDATE_CHECK_RETRY_INTERVAL_MS = 60_000L // 1 minute

data class DeviceConnectionManagerConfig(
    val backgroundPauseDelayMs: Long = DEFAULT_BACKGROUND_PAUSE_DELAY_MS,
    val lastSeenPersistIntervalMs: Long = DEFAULT_LAST_SEEN_PERSIST_INTERVAL_MS,
    val updateCheckCacheTtlMs: Long = DEFAULT_UPDATE_CHECK_CACHE_TTL_MS,
    val updateCheckRetryIntervalMs: Long = DEFAULT_UPDATE_CHECK_RETRY_INTERVAL_MS,
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

    private class DeviceSession(
        var address: String,
        var connection: DeviceConnection,
        var connectionJob: Job? = null,
        var updateCheckJob: Job? = null,
        var lastSeenPersisted: Long = 0L,
        var nextUpdateCheckAt: Long = 0L,
    ) {
        fun cancelJobs() {
            connectionJob?.cancel()
            updateCheckJob?.cancel()
        }
    }

    private val lock = SynchronizedObject()
    private val sessions = mutableMapOf<String, DeviceSession>()
    private val resolvedRepositoryIds = mutableMapOf<String, Long>()

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

    private fun removeTrackedDeviceLocked(mac: String): DeviceConnection? =
        sessions.remove(mac)?.also { it.cancelJobs() }?.connection

    private fun removeDeletedConnections(newMacs: Set<String>) {
        val toDestroy = synchronized(lock) {
            val removedMacs = sessions.keys - newMacs
            removedMacs.mapNotNull { mac -> removeTrackedDeviceLocked(mac) }
        }
        toDestroy.forEach { it.destroy() }
    }

    private fun createOrUpdateConnections(newDeviceMap: Map<String, Device>): List<DeviceConnection> {
        val toConnect = mutableListOf<DeviceConnection>()
        val toDestroy = mutableListOf<DeviceConnection>()
        val toObserve = mutableListOf<Pair<String, DeviceConnection>>()

        synchronized(lock) {
            for ((mac, device) in newDeviceMap) {
                val existing = sessions[mac]
                if (existing == null || existing.address != device.address) {
                    val newConn = connectionFactory.createConnection(device.address, managerScope)
                    if (existing != null) {
                        logger.d { "Address changed for $mac to ${device.address}; reconnecting" }
                        existing.connectionJob?.cancel()
                        existing.connectionJob = null
                        toDestroy.add(existing.connection)
                        existing.address = device.address
                        existing.connection = newConn
                    } else {
                        sessions[mac] = DeviceSession(
                            address = device.address,
                            connection = newConn,
                            lastSeenPersisted = device.lastSeen,
                        )
                    }
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
            for (device in newDeviceList) {
                val session = sessions[device.macAddress] ?: continue
                if (device.lastSeen > session.lastSeenPersisted) {
                    session.lastSeenPersisted = device.lastSeen
                }
            }
            sessions.mapValues { (_, session) -> session.connection.status.value }
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
            val session = sessions[mac] ?: return
            session.nextUpdateCheckAt = 0L
            session.updateCheckJob?.cancel()
            session.updateCheckJob = managerScope.launch {
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
                    runCatchingNonCancellation({ "Error processing frame for $mac" }) {
                        onIncomingStateInfo(mac, connection, stateInfo)
                    }
                }
            }
        }
        synchronized(lock) {
            val session = sessions[mac]
            if (session != null && session.connection === connection) {
                session.connectionJob?.cancel()
                session.connectionJob = job
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

    private inline fun <T> runCatchingNonCancellation(errorMessage: () -> String, block: () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        logger.e(e) { errorMessage() }
        null
    }

    private fun onConnectionStatusChanged(mac: String, connection: DeviceConnection, status: WebsocketStatus) {
        if (synchronized(lock) { sessions[mac]?.connection !== connection }) return
        updateDevice(mac) { it.copy(websocketStatus = status) }
    }

    private suspend fun onIncomingStateInfo(mac: String, connection: DeviceConnection, stateInfo: DeviceStateInfo) {
        if (synchronized(lock) { sessions[mac]?.connection !== connection }) return
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

    private suspend fun resolveRepositoryIdSafe(repoStr: String, fallback: Long): Long {
        synchronized(lock) { resolvedRepositoryIds[repoStr] }?.let { return it }
        return runCatchingNonCancellation({ "Failed to resolve repository ID for $repoStr" }) {
            val resolved = repositoryIdResolver.getOrCreateRepositoryId(repoStr)
            synchronized(lock) {
                resolvedRepositoryIds[repoStr] = resolved
            }
            resolved
        } ?: fallback
    }

    private suspend fun persistMetadataChanges(
        mac: String,
        currentDevice: Device,
        name: String,
        branch: Branch,
        repoId: Long,
    ) {
        if (name.isNotBlank() && name != currentDevice.originalName) {
            runCatchingNonCancellation({ "Failed to update originalName for $mac" }) {
                store.updateOriginalName(mac, name)
            }
        }

        if (branch != currentDevice.branch) {
            runCatchingNonCancellation({ "Failed to update branch for $mac" }) {
                store.updateBranch(mac, branch)
            }
        }

        if (repoId != currentDevice.repositoryId) {
            runCatchingNonCancellation({ "Failed to update repositoryId for $mac" }) {
                store.updateRepositoryId(mac, repoId)
            }
        }
    }

    private suspend fun persistThrottledLastSeen(mac: String, now: Long) {
        val shouldPersist = synchronized(lock) {
            val session = sessions[mac] ?: return
            if (now - session.lastSeenPersisted >= config.lastSeenPersistIntervalMs) {
                session.lastSeenPersisted = now
                true
            } else {
                false
            }
        }
        if (shouldPersist) {
            runCatchingNonCancellation({ "Failed to update lastSeen for $mac" }) {
                store.updateLastSeen(mac, now)
            }
        }
    }

    private fun updateInMemoryState(
        mac: String,
        updatedDevice: Device,
        stateInfo: DeviceStateInfo,
        updateTag: String?,
    ) {
        updateDevice(mac) { item ->
            val mergedDevice = item.device.copy(
                originalName = updatedDevice.originalName,
                branch = if (item.device.branch == Branch.UNKNOWN) updatedDevice.branch else item.device.branch,
                repositoryId = updatedDevice.repositoryId,
                lastSeen = maxOf(item.device.lastSeen, updatedDevice.lastSeen),
            )
            val finalTag = if (mergedDevice.skipUpdateTag.isNotEmpty() &&
                mergedDevice.skipUpdateTag == updateTag
            ) {
                null
            } else {
                updateTag
            }
            item.copy(
                device = mergedDevice,
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
        val shouldSkipCheck = synchronized(lock) {
            val session = sessions[mac] ?: return current.updateVersionTag
            if (!metadataChanged && now < session.nextUpdateCheckAt) {
                true
            } else {
                session.nextUpdateCheckAt = now + config.updateCheckRetryIntervalMs
                false
            }
        }

        if (shouldSkipCheck) {
            return current.updateVersionTag
        }

        var checkSucceeded = false
        val resolvedTag = runCatchingNonCancellation({ "Failed to check update for $mac" }) {
            val tag = updateTagResolver.checkForUpdate(deviceToUse, stateInfo)
            synchronized(lock) {
                sessions[mac]?.nextUpdateCheckAt = now + config.updateCheckCacheTtlMs
            }
            checkSucceeded = true
            tag
        }
        return if (checkSucceeded) resolvedTag else current.updateVersionTag
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
            sessions.values.map { it.connection }.filter { it.status.value == WebsocketStatus.DISCONNECTED }
        }
        offline.forEach { it.connect() }
    }

    /**
     * Invalidates cached update-check timestamps and re-evaluates update availability
     * for all devices that currently have state info.
     *
     * Call this after refreshing release metadata from GitHub so connected devices
     * immediately reflect newly downloaded releases.
     */
    fun recheckUpdates() {
        val currentDevices = _allDevicesWithState.value
        for (item in currentDevices) {
            val stateInfo = item.stateInfo ?: continue
            scheduleModifiedUpdateCheck(
                mac = item.device.macAddress,
                device = item.device,
                previous = item,
                stateInfo = stateInfo,
            )
        }
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
                    sessions.values.map { it.connection }
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
                sessions.values.map { it.connection }
            } else {
                emptyList()
            }
        }
        connectionsToResume.forEach { it.resume() }
    }

    fun onPause() = onBackground()

    fun onResume() = onForeground()

    suspend fun flushLastSeen() {
        val currentDevices = _allDevicesWithState.value
        val toPersist: List<Pair<String, Long>> = synchronized(lock) {
            currentDevices.mapNotNull { item ->
                val mac = item.device.macAddress
                val session = sessions[mac] ?: return@mapNotNull null
                val currentLastSeen = item.device.lastSeen
                if (currentLastSeen > session.lastSeenPersisted) {
                    session.lastSeenPersisted = currentLastSeen
                    mac to currentLastSeen
                } else {
                    null
                }
            }
        }
        for ((mac, lastSeen) in toPersist) {
            runCatchingNonCancellation({ "Failed to flush lastSeen for $mac" }) {
                store.updateLastSeen(mac, lastSeen)
            }
        }
    }

    suspend fun deleteDevice(device: Device) {
        val connection = synchronized(lock) {
            removeTrackedDeviceLocked(device.macAddress)
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
        val connection = synchronized(lock) { sessions[macAddress]?.connection } ?: return false

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
            resolvedRepositoryIds.clear()
            val connections = sessions.values.map { it.connection }
            sessions.clear()
            connections
        }
        toDestroy.forEach { it.destroy() }
    }

    companion object
}
