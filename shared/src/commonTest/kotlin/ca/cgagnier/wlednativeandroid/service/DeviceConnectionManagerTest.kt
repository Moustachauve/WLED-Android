package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceStore
import ca.cgagnier.wlednativeandroid.service.update.RepositoryIdResolver
import ca.cgagnier.wlednativeandroid.service.update.UpdateTagResolver
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnection
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceConnectionManagerTest {

    private class FakeDeviceStore : DeviceStore {
        val allDevicesFlow = MutableStateFlow<List<Device>>(emptyList())
        override val allDevices: Flow<List<Device>> = allDevicesFlow

        val updatedOriginalNames = mutableMapOf<String, String>()
        val updatedBranches = mutableMapOf<String, Branch>()
        val updatedRepositoryIds = mutableMapOf<String, Long>()
        val updatedLastSeens = mutableMapOf<String, Long>()
        val deletedDevices = mutableListOf<Device>()

        override suspend fun updateOriginalName(macAddress: String, originalName: String) {
            updatedOriginalNames[macAddress] = originalName
        }

        override suspend fun updateBranch(macAddress: String, branch: Branch) {
            updatedBranches[macAddress] = branch
        }

        override suspend fun updateRepositoryId(macAddress: String, repositoryId: Long) {
            updatedRepositoryIds[macAddress] = repositoryId
        }

        override suspend fun updateLastSeen(macAddress: String, lastSeen: Long) {
            updatedLastSeens[macAddress] = lastSeen
        }

        override suspend fun delete(device: Device) {
            deletedDevices.add(device)
            allDevicesFlow.update { list -> list.filterNot { it.macAddress == device.macAddress } }
        }
    }

    private class FakeDeviceConnection(val address: String) : DeviceConnection {
        private val _status = MutableStateFlow(WebsocketStatus.DISCONNECTED)
        override val status: StateFlow<WebsocketStatus> = _status.asStateFlow()

        private val _incomingStateInfo = MutableSharedFlow<DeviceStateInfo>(replay = 1, extraBufferCapacity = 16)
        override val incomingStateInfo: SharedFlow<DeviceStateInfo> = _incomingStateInfo.asSharedFlow()

        var connectCallCount = 0
        var pauseCallCount = 0
        var resumeCallCount = 0
        var destroyCallCount = 0
        var sendStateResult = true
        var sendStateDeferred: CompletableDeferred<Boolean>? = null
        var onConnect: (() -> Unit)? = null
        val sentStates = mutableListOf<State>()

        override fun connect() {
            connectCallCount++
            _status.value = WebsocketStatus.CONNECTED
            onConnect?.invoke()
        }

        override fun pause() {
            pauseCallCount++
            _status.value = WebsocketStatus.DISCONNECTED
        }

        override fun resume() {
            resumeCallCount++
            _status.value = WebsocketStatus.CONNECTED
        }

        override fun destroy() {
            destroyCallCount++
            _status.value = WebsocketStatus.DISCONNECTED
        }

        override suspend fun sendState(state: State): Boolean {
            sentStates.add(state)
            return sendStateDeferred?.await() ?: sendStateResult
        }

        fun emitStateInfo(stateInfo: DeviceStateInfo) {
            _incomingStateInfo.tryEmit(stateInfo)
        }
    }

    private class FakeDeviceConnectionFactory : DeviceConnectionFactory {
        val createdConnections = mutableMapOf<String, FakeDeviceConnection>()
        var onConnectionCreated: ((FakeDeviceConnection) -> Unit)? = null

        override fun createConnection(address: String, scope: CoroutineScope): DeviceConnection {
            val connection = FakeDeviceConnection(address)
            onConnectionCreated?.invoke(connection)
            createdConnections[address] = connection
            return connection
        }
    }

    private class FakeUpdateTagResolver : UpdateTagResolver {
        var tagToReturn: String? = null
        var failureToThrow: Exception? = null
        var callCount = 0

        override suspend fun checkForUpdate(device: Device, stateInfo: DeviceStateInfo?): String? {
            callCount++
            failureToThrow?.let { throw it }
            return tagToReturn
        }
    }

    private class FakeRepositoryIdResolver : RepositoryIdResolver {
        var idToReturn: Long = 1L
        var callCount = 0

        override suspend fun getOrCreateRepositoryId(ownerAndRepo: String): Long {
            callCount++
            return idToReturn
        }
    }

    private val store = FakeDeviceStore()
    private val factory = FakeDeviceConnectionFactory()
    private val connectivity = FakeNetworkConnectivityManager(isConnected = true)
    private val updateResolver = FakeUpdateTagResolver()
    private val repoIdResolver = FakeRepositoryIdResolver()

    private fun TestScope.createManager(
        timeProvider: () -> Long = { 10_000L },
        config: DeviceConnectionManagerConfig = DeviceConnectionManagerConfig(
            backgroundPauseDelayMs = 2_000L,
            lastSeenPersistIntervalMs = 30_000L,
        ),
        updateTagResolver: FakeUpdateTagResolver = updateResolver,
        repositoryIdResolver: FakeRepositoryIdResolver = repoIdResolver,
    ): DeviceConnectionManager {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        coroutineContext.job.invokeOnCompletion { scope.cancel() }
        return DeviceConnectionManager(
            store = store,
            connectionFactory = factory,
            connectivity = connectivity,
            updateTagResolver = updateTagResolver,
            repositoryIdResolver = repositoryIdResolver,
            coroutineScope = scope,
            timeProvider = timeProvider,
            backgroundDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            config = config,
        )
    }

    @Suppress("LongParameterList")
    private fun createDevice(
        mac: String = "AABBCCDDEEFF",
        address: String = "192.168.1.100",
        originalName: String = "WLED-Light",
        customName: String = "",
        branch: Branch = Branch.UNKNOWN,
        lastSeen: Long = 10_000L,
        repositoryId: Long = 1L,
        skipUpdateTag: String = "",
    ): Device = Device(
        macAddress = mac,
        address = address,
        originalName = originalName,
        customName = customName,
        branch = branch,
        lastSeen = lastSeen,
        repositoryId = repositoryId,
        skipUpdateTag = skipUpdateTag,
    )

    private fun createStateInfo(
        name: String = "WLED-Light",
        version: String = "0.14.0",
        brightness: Int = 128,
        isOn: Boolean = true,
        repository: String? = null,
    ): DeviceStateInfo = DeviceStateInfo(
        state = State(isOn = isOn, brightness = brightness),
        info = Info(
            version = version,
            name = name,
            leds = Leds(count = 30),
            wifi = Wifi(),
            repository = repository,
        ),
    )

    @Test
    fun syncDevicesCreatesConnectionAndConnectsWhenForegrounded() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.50"]
        assertEquals(1, connection?.connectCallCount)
        assertEquals(1, manager.allDevicesWithState.value.size)
        assertEquals(WebsocketStatus.CONNECTED, manager.allDevicesWithState.value[0].websocketStatus)
    }

    @Test
    fun syncDevicesCancelsAndDestroysConnectionWhenDeviceRemoved() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.50"]

        store.allDevicesFlow.value = emptyList()
        advanceUntilIdle()

        assertEquals(1, connection?.destroyCallCount)
        assertTrue(manager.allDevicesWithState.value.isEmpty())
    }

    @Test
    fun syncDevicesReconnectsWhenDeviceAddressChanges() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()
        val oldConn = factory.createdConnections["192.168.1.50"]

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.60"))
        advanceUntilIdle()
        val newConn = factory.createdConnections["192.168.1.60"]

        assertEquals(1, oldConn?.destroyCallCount)
        assertEquals(1, newConn?.connectCallCount)
        assertEquals("192.168.1.60", manager.allDevicesWithState.value[0].device.address)
    }

    @Test
    fun syncDevicesPreservesNewerInMemoryLastSeenOverOlderDatabaseEmission() = runTest {
        var currentTime = 10_000L
        val manager = createManager(timeProvider = { currentTime })

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 5_000L))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 5_000L))
        advanceUntilIdle()

        assertEquals(10_000L, manager.allDevicesWithState.value[0].device.lastSeen)
    }

    @Test
    fun incomingFrameUpdatesOriginalNameInStoreWhenNameChanges() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", originalName = "Old Name"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(name = "New Name"))
        advanceUntilIdle()

        assertEquals("New Name", store.updatedOriginalNames["A"])
        assertEquals("New Name", manager.allDevicesWithState.value[0].device.originalName)
    }

    @Test
    fun incomingFrameUpdatesBranchInStoreWhenVersionIsBeta() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", branch = Branch.UNKNOWN))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(version = "0.14.0-b1"))
        advanceUntilIdle()

        assertEquals(Branch.BETA, store.updatedBranches["A"])
        assertEquals(Branch.BETA, manager.allDevicesWithState.value[0].device.branch)
    }

    @Test
    fun incomingFrameResolvesAndPersistsRepositoryIdAndCachesLookup() = runTest {
        repoIdResolver.idToReturn = 42L
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", repositoryId = 1L))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(repository = "custom/wled-fork"))
        advanceUntilIdle()

        assertEquals(1, repoIdResolver.callCount)
        assertEquals(42L, store.updatedRepositoryIds["A"])
        assertEquals(42L, manager.allDevicesWithState.value[0].device.repositoryId)

        connection.emitStateInfo(createStateInfo(repository = "custom/wled-fork", brightness = 200))
        advanceUntilIdle()

        assertEquals(1, repoIdResolver.callCount)
    }

    @Test
    fun incomingFrameThrottlesLastSeenWriteUntilIntervalElapses() = runTest {
        var currentTime = 10_000L
        createManager(
            timeProvider = { currentTime },
            config = DeviceConnectionManagerConfig(lastSeenPersistIntervalMs = 30_000L),
        )

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 0L))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        currentTime = 35_000L
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()
        assertEquals(35_000L, store.updatedLastSeens["A"])

        currentTime = 40_000L
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()
        assertEquals(35_000L, store.updatedLastSeens["A"])

        currentTime = 66_000L
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()
        assertEquals(66_000L, store.updatedLastSeens["A"])
    }

    @Test
    fun flushLastSeenPersistsNewerInMemoryLastSeenToStore() = runTest {
        var currentTime = 10_000L
        val manager = createManager(timeProvider = { currentTime })

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 10_000L))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        currentTime = 15_000L
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()

        manager.flushLastSeen()
        advanceUntilIdle()

        assertEquals(15_000L, store.updatedLastSeens["A"])
    }

    @Test
    fun flushLastSeenDoesNotPersistUnchangedDatabaseTimestamp() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 10_000L))
        advanceUntilIdle()

        manager.flushLastSeen()
        advanceUntilIdle()

        assertTrue(store.updatedLastSeens.isEmpty())
    }

    @Test
    fun incomingFrameChecksForUpdateAndCachesResultWithinTtl() = runTest {
        var currentTime = 10_000L
        updateResolver.tagToReturn = "0.15.0"
        val manager = createManager(
            timeProvider = { currentTime },
            config = DeviceConnectionManagerConfig(updateCheckCacheTtlMs = 3_600_000L),
        )

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(version = "0.14.0"))
        advanceUntilIdle()

        assertEquals(1, updateResolver.callCount)
        assertEquals("0.15.0", manager.allDevicesWithState.value[0].updateVersionTag)

        currentTime = 20_000L
        connection.emitStateInfo(createStateInfo(version = "0.14.0", brightness = 200))
        advanceUntilIdle()

        assertEquals(1, updateResolver.callCount)

        currentTime = 3_615_000L
        connection.emitStateInfo(createStateInfo(version = "0.14.0", brightness = 220))
        advanceUntilIdle()

        assertEquals(2, updateResolver.callCount)
    }

    @Test
    fun incomingFrameInvalidatesUpdateCacheWhenFirmwareVersionChanges() = runTest {
        var currentTime = 10_000L
        updateResolver.tagToReturn = "0.15.0"
        val manager = createManager(timeProvider = { currentTime })

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(version = "0.14.0"))
        advanceUntilIdle()
        assertEquals(1, updateResolver.callCount)

        updateResolver.tagToReturn = null
        currentTime = 12_000L
        connection.emitStateInfo(createStateInfo(version = "0.15.0"))
        advanceUntilIdle()

        assertEquals(2, updateResolver.callCount)
        assertNull(manager.allDevicesWithState.value[0].updateVersionTag)
    }

    @Test
    fun incomingFrameRetriesUpdateCheckAfterFailureOnceRetryIntervalElapses() = runTest {
        var currentTime = 10_000L
        updateResolver.failureToThrow = IllegalStateException("DB error")
        val manager = createManager(
            timeProvider = { currentTime },
            config = DeviceConnectionManagerConfig(updateCheckRetryIntervalMs = 60_000L),
        )

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()
        assertEquals(1, updateResolver.callCount)

        updateResolver.failureToThrow = null
        updateResolver.tagToReturn = "0.15.0"
        currentTime = 30_000L
        connection.emitStateInfo(createStateInfo(brightness = 150))
        advanceUntilIdle()
        assertEquals(1, updateResolver.callCount)

        currentTime = 71_000L
        connection.emitStateInfo(createStateInfo(brightness = 180))
        advanceUntilIdle()
        assertEquals(2, updateResolver.callCount)
        assertEquals("0.15.0", manager.allDevicesWithState.value[0].updateVersionTag)
    }

    @Test
    fun syncDevicesRechecksUpdateWhenBranchChangesOrSkipTagCleared() = runTest {
        updateResolver.tagToReturn = "0.15.0"
        val manager = createManager()

        store.allDevicesFlow.value = listOf(
            createDevice(mac = "A", branch = Branch.STABLE, skipUpdateTag = "0.15.0"),
        )
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(version = "0.14.0"))
        advanceUntilIdle()
        assertNull(manager.allDevicesWithState.value[0].updateVersionTag)

        store.allDevicesFlow.value = listOf(
            createDevice(mac = "A", branch = Branch.STABLE, skipUpdateTag = ""),
        )
        advanceUntilIdle()

        assertEquals(2, updateResolver.callCount)
        assertEquals("0.15.0", manager.allDevicesWithState.value[0].updateVersionTag)
    }

    @Test
    fun onBackgroundPausesConnectionsAfterDebounceDelay() = runTest {
        var currentTime = 10_000L
        val manager = createManager(
            timeProvider = { currentTime },
            config = DeviceConnectionManagerConfig(backgroundPauseDelayMs = 2_000L),
        )

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 10_000L))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.100"]!!

        currentTime = 12_000L
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()

        manager.onBackground()
        runCurrent()

        assertEquals(12_000L, store.updatedLastSeens["A"])
        assertFalse(manager.isPaused.value)
        assertEquals(0, connection.pauseCallCount)

        advanceTimeBy(2_000L)
        runCurrent()

        assertTrue(manager.isPaused.value)
        assertEquals(1, connection.pauseCallCount)
    }

    @Test
    fun onForegroundCancelsPendingBackgroundPauseWithinDebounceDelay() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.100"]!!

        manager.onBackground()
        advanceTimeBy(1_000L)
        manager.onForeground()
        advanceTimeBy(2_000L)
        advanceUntilIdle()

        assertFalse(manager.isPaused.value)
        assertEquals(0, connection.pauseCallCount)
    }

    @Test
    fun onForegroundResumesConnectionsIfWasPaused() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.100"]!!

        manager.onBackground()
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertTrue(manager.isPaused.value)

        manager.onForeground()
        advanceUntilIdle()

        assertFalse(manager.isPaused.value)
        assertEquals(1, connection.resumeCallCount)
    }

    @Test
    fun sendStateAppliesOptimisticStateImmediatelyAndReturnsTrue() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(brightness = 100))
        advanceUntilIdle()

        val success = manager.setBrightness("A", 255)
        assertTrue(success)
        assertEquals(255, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)
        assertEquals(1, connection.sentStates.size)
        assertEquals(255, connection.sentStates[0].brightness)
    }

    @Test
    fun sendStateRollsBackOptimisticStateWhenConnectionFails() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(brightness = 100))
        advanceUntilIdle()

        connection.sendStateResult = false
        val success = manager.setBrightness("A", 255)
        assertFalse(success)
        assertEquals(100, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)
    }

    @Test
    fun refreshOfflineDevicesReconnectsOnlyDisconnectedConnections() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(
            createDevice(mac = "A", address = "192.168.1.50"),
            createDevice(mac = "B", address = "192.168.1.60"),
        )
        advanceUntilIdle()

        val connA = factory.createdConnections["192.168.1.50"]!!
        val connB = factory.createdConnections["192.168.1.60"]!!
        connB.pause()
        advanceUntilIdle()

        val initialConnectsA = connA.connectCallCount
        val initialConnectsB = connB.connectCallCount

        manager.refreshOfflineDevices()
        advanceUntilIdle()

        assertEquals(initialConnectsA, connA.connectCallCount)
        assertEquals(initialConnectsB + 1, connB.connectCallCount)
    }

    @Test
    fun networkRecoveryReconnectsDisconnectedConnectionsWhenForegrounded() = runTest {
        connectivity.setConnected(false)
        createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()

        val conn = factory.createdConnections["192.168.1.50"]!!
        conn.pause()
        advanceUntilIdle()

        val previousConnects = conn.connectCallCount
        connectivity.setConnected(true)
        advanceUntilIdle()

        assertEquals(previousConnects + 1, conn.connectCallCount)
    }

    @Test
    fun deleteDeviceCancelsConnectionAndDeletesFromStore() = runTest {
        val manager = createManager()

        val device = createDevice(mac = "A", address = "192.168.1.50")
        store.allDevicesFlow.value = listOf(device)
        advanceUntilIdle()

        val conn = factory.createdConnections["192.168.1.50"]!!
        manager.deleteDevice(device)
        advanceUntilIdle()

        assertEquals(1, conn.destroyCallCount)
        assertEquals(1, store.deletedDevices.size)
        assertEquals("A", store.deletedDevices[0].macAddress)
        assertTrue(manager.allDevicesWithState.value.isEmpty())
    }

    @Test
    fun sendStateGuardedRollbackPreservesNewerFrameWhenConnectionFails() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(brightness = 100))
        advanceUntilIdle()

        val deferred = CompletableDeferred<Boolean>()
        connection.sendStateDeferred = deferred

        val sendJob = launch {
            val success = manager.setBrightness("A", 255)
            assertFalse(success)
        }
        runCurrent()

        assertEquals(255, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)

        connection.emitStateInfo(createStateInfo(brightness = 150))
        runCurrent()
        assertEquals(150, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)

        deferred.complete(false)
        sendJob.join()

        assertEquals(150, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)
    }

    @Test
    fun destroyCancelsCollectorsAndCleansUpConnections() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.50"]!!

        manager.destroy()
        advanceUntilIdle()

        assertEquals(1, connection.destroyCallCount)

        store.allDevicesFlow.value = listOf(createDevice(mac = "B", address = "192.168.1.60"))
        advanceUntilIdle()

        assertFalse(factory.createdConnections.containsKey("192.168.1.60"))
    }

    @Test
    fun refreshOfflineDevicesDoesNotReconnectWhenPaused() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()

        val conn = factory.createdConnections["192.168.1.50"]!!
        val initialConnects = conn.connectCallCount

        manager.onBackground()
        advanceTimeBy(2_000L)
        advanceUntilIdle()
        assertTrue(manager.isPaused.value)

        manager.refreshOfflineDevices()
        advanceUntilIdle()

        assertEquals(initialConnects, conn.connectCallCount)
    }

    @Test
    fun syncDevicesProcessesIncomingFrameEmittedImmediatelyOnConnect() = runTest {
        factory.onConnectionCreated = { conn ->
            conn.onConnect = {
                conn.emitStateInfo(createStateInfo(brightness = 42))
            }
        }
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()

        assertEquals(1, manager.allDevicesWithState.value.size)
        assertEquals(42, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)
    }

    @Test
    fun staleConnectionStatusEmissionsDoNotOverwriteNewConnectionStatus() = runTest {
        val manager = createManager()

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()
        val oldConn = factory.createdConnections["192.168.1.50"]!!

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.60"))
        advanceUntilIdle()

        assertEquals(WebsocketStatus.CONNECTED, manager.allDevicesWithState.value[0].websocketStatus)

        oldConn.pause()
        advanceUntilIdle()

        assertEquals(WebsocketStatus.CONNECTED, manager.allDevicesWithState.value[0].websocketStatus)
    }

    private fun TestScope.connectSingleDeviceWithVersion(
        initialUpdateTag: String?,
        version: String = "0.14.0",
    ): DeviceConnectionManager {
        updateResolver.tagToReturn = initialUpdateTag
        val manager = createManager()
        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()
        factory.createdConnections["192.168.1.50"]!!.emitStateInfo(createStateInfo(version = version))
        advanceUntilIdle()
        return manager
    }

    @Test
    fun recheckUpdatesInvalidatesCacheAndResolvesNewUpdateTag() = runTest {
        val manager = connectSingleDeviceWithVersion(initialUpdateTag = null)

        updateResolver.tagToReturn = "0.15.0"
        manager.recheckUpdates()
        advanceUntilIdle()

        assertEquals("0.15.0", manager.allDevicesWithState.value[0].updateVersionTag)
    }

    @Test
    fun markUpdateInstalledUpdatesStateInfoVersion() = runTest {
        val manager = connectSingleDeviceWithVersion(initialUpdateTag = "0.15.0")

        manager.markUpdateInstalled(macAddress = "A", installedVersionTag = "v0.15.0")

        assertEquals("0.15.0", manager.allDevicesWithState.value[0].stateInfo?.info?.version)
    }

    @Test
    fun markUpdateInstalledClearsUpdateVersionTag() = runTest {
        val manager = connectSingleDeviceWithVersion(initialUpdateTag = "0.15.0")

        manager.markUpdateInstalled(macAddress = "A", installedVersionTag = "v0.15.0")

        assertEquals(null, manager.allDevicesWithState.value[0].updateVersionTag)
    }
}
