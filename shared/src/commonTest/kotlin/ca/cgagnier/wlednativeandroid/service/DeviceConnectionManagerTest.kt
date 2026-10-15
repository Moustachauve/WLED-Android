package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceStore
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnection
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlinx.coroutines.CoroutineDispatcher
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
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        }
    }

    private class FakeDeviceConnection(val address: String) : DeviceConnection {
        private val _status = MutableStateFlow(WebsocketStatus.DISCONNECTED)
        override val status: StateFlow<WebsocketStatus> = _status.asStateFlow()

        private val _incomingStateInfo = MutableSharedFlow<DeviceStateInfo>(extraBufferCapacity = 16)
        override val incomingStateInfo: SharedFlow<DeviceStateInfo> = _incomingStateInfo.asSharedFlow()

        var connectCallCount = 0
        var pauseCallCount = 0
        var resumeCallCount = 0
        var destroyCallCount = 0
        var sendStateResult = true
        val sentStates = mutableListOf<State>()

        override fun connect() {
            connectCallCount++
            _status.value = WebsocketStatus.CONNECTED
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
            return sendStateResult
        }

        fun emitStateInfo(stateInfo: DeviceStateInfo) {
            _incomingStateInfo.tryEmit(stateInfo)
        }
    }

    private class FakeDeviceConnectionFactory : DeviceConnectionFactory {
        val createdConnections = mutableMapOf<String, FakeDeviceConnection>()

        override fun createConnection(address: String, scope: CoroutineScope): DeviceConnection {
            val connection = FakeDeviceConnection(address)
            createdConnections[address] = connection
            return connection
        }
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
    ): Device = Device(
        macAddress = mac,
        address = address,
        originalName = originalName,
        customName = customName,
        branch = branch,
        lastSeen = lastSeen,
        repositoryId = repositoryId,
    )

    private fun createStateInfo(
        name: String = "WLED-Light",
        version: String = "0.14.0",
        brightness: Int = 128,
        isOn: Boolean = true,
    ): DeviceStateInfo = DeviceStateInfo(
        state = State(isOn = isOn, brightness = brightness),
        info = Info(
            version = version,
            name = name,
            leds = Leds(count = 30),
            wifi = Wifi(),
        ),
    )

    @Suppress("LongParameterList")
    private fun createManager(
        testScope: TestScope,
        testDispatcher: CoroutineDispatcher,
        store: FakeDeviceStore,
        factory: FakeDeviceConnectionFactory,
        connectivity: FakeNetworkConnectivityManager,
        timeProvider: () -> Long = { 10_000L },
        config: DeviceConnectionManagerConfig = DeviceConnectionManagerConfig(
            backgroundPauseDelayMs = 2_000L,
        ),
    ): DeviceConnectionManager {
        val scope = CoroutineScope(testDispatcher + SupervisorJob())
        testScope.coroutineContext.job.invokeOnCompletion { scope.cancel() }
        return DeviceConnectionManager(
            store = store,
            connectionFactory = factory,
            connectivity = connectivity,
            coroutineScope = scope,
            timeProvider = timeProvider,
            backgroundDispatcher = testDispatcher,
            ioDispatcher = testDispatcher,
            config = config,
        )
    }

    @Test
    fun syncDevicesCreatesConnectionAndConnectsWhenForegrounded() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.50"]
        assertEquals(1, connection?.connectCallCount)
        assertEquals(1, manager.allDevicesWithState.value.size)
        assertEquals(WebsocketStatus.CONNECTED, manager.allDevicesWithState.value[0].websocketStatus)
    }

    @Test
    fun syncDevicesCancelsAndDestroysConnectionWhenDeviceRemoved() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

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
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

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
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        var currentTime = 10_000L
        val manager = createManager(this, testDispatcher, store, factory, connectivity, timeProvider = { currentTime })

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 5_000L))
        advanceUntilIdle()

        // Incoming frame updates in-memory lastSeen to 10_000L
        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo())
        advanceUntilIdle()

        // DB emits an update with an older timestamp (e.g. 5_000L)
        store.allDevicesFlow.value = listOf(createDevice(mac = "A", lastSeen = 5_000L))
        advanceUntilIdle()

        assertEquals(10_000L, manager.allDevicesWithState.value[0].device.lastSeen)
    }

    @Test
    fun onBackgroundPausesConnectionsAfterDebounceDelay() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(
            this,
            testDispatcher,
            store,
            factory,
            connectivity,
            config = DeviceConnectionManagerConfig(backgroundPauseDelayMs = 2_000L),
        )

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.100"]!!

        manager.onBackground()
        runCurrent()

        // Still not paused yet because of 2000ms debounce
        assertFalse(manager.isPaused.value)
        assertEquals(0, connection.pauseCallCount)

        // Advance debounce time
        advanceTimeBy(2_000L)
        runCurrent()

        assertTrue(manager.isPaused.value)
        assertEquals(1, connection.pauseCallCount)
    }

    @Test
    fun onForegroundCancelsPendingBackgroundPauseWithinDebounceDelay() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(
            this,
            testDispatcher,
            store,
            factory,
            connectivity,
            config = DeviceConnectionManagerConfig(backgroundPauseDelayMs = 2_000L),
        )

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()
        val connection = factory.createdConnections["192.168.1.100"]!!

        manager.onBackground()
        advanceTimeBy(1_000L) // Only 1s elapsed
        manager.onForeground()
        advanceTimeBy(2_000L) // Advance beyond original 2s
        advanceUntilIdle()

        assertFalse(manager.isPaused.value)
        assertEquals(0, connection.pauseCallCount)
    }

    @Test
    fun onForegroundResumesConnectionsIfWasPaused() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(
            this,
            testDispatcher,
            store,
            factory,
            connectivity,
            config = DeviceConnectionManagerConfig(backgroundPauseDelayMs = 2_000L),
        )

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
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

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
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

        store.allDevicesFlow.value = listOf(createDevice(mac = "A"))
        advanceUntilIdle()

        val connection = factory.createdConnections["192.168.1.100"]!!
        connection.emitStateInfo(createStateInfo(brightness = 100))
        advanceUntilIdle()

        connection.sendStateResult = false
        val success = manager.setBrightness("A", 255)
        assertFalse(success)
        // Guarded rollback returns state to 100
        assertEquals(100, manager.allDevicesWithState.value[0].stateInfo?.state?.brightness)
    }

    @Test
    fun refreshOfflineDevicesReconnectsOnlyDisconnectedConnections() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

        store.allDevicesFlow.value = listOf(
            createDevice(mac = "A", address = "192.168.1.50"),
            createDevice(mac = "B", address = "192.168.1.60"),
        )
        advanceUntilIdle()

        val connA = factory.createdConnections["192.168.1.50"]!!
        val connB = factory.createdConnections["192.168.1.60"]!!
        // Manually pause B so it's DISCONNECTED
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
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = false)
        createManager(this, testDispatcher, store, factory, connectivity)

        store.allDevicesFlow.value = listOf(createDevice(mac = "A", address = "192.168.1.50"))
        advanceUntilIdle()

        val conn = factory.createdConnections["192.168.1.50"]!!
        conn.pause() // DISCONNECTED
        advanceUntilIdle()

        val previousConnects = conn.connectCallCount
        connectivity.setConnected(true)
        advanceUntilIdle()

        assertEquals(previousConnects + 1, conn.connectCallCount)
    }

    @Test
    fun deleteDeviceCancelsConnectionAndDeletesFromStore() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val store = FakeDeviceStore()
        val factory = FakeDeviceConnectionFactory()
        val connectivity = FakeNetworkConnectivityManager(isConnected = true)
        val manager = createManager(this, testDispatcher, store, factory, connectivity)

        val device = createDevice(mac = "A", address = "192.168.1.50")
        store.allDevicesFlow.value = listOf(device)
        advanceUntilIdle()

        val conn = factory.createdConnections["192.168.1.50"]!!
        manager.deleteDevice(device)
        advanceUntilIdle()

        assertEquals(1, conn.destroyCallCount)
        assertEquals(1, store.deletedDevices.size)
        assertEquals("A", store.deletedDevices[0].macAddress)
    }
}
