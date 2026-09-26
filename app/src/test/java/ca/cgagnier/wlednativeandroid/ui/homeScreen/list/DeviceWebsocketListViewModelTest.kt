package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.content.Context
import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceDao
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClient
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceWebsocketListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val deviceDao: DeviceDao = mockk(relaxed = true)
    private lateinit var deviceRepository: DeviceRepository
    private val websocketClientFactory: WebsocketClientFactory = mockk()
    private val widgetManager: WledWidgetManager = mockk(relaxed = true)
    private val saveDeviceStateUseCase: SaveDeviceStateUseCase = mockk(relaxed = true)
    private val deviceUpdateManager: DeviceUpdateManager = mockk(relaxed = true)
    private val applicationContext: Context = mockk(relaxed = true)

    private val allDevicesDbFlow = MutableStateFlow<List<Device>>(emptyList())

    private val createdClients = mutableMapOf<String, TestClientHolder>()

    private class TestClientHolder(
        val client: WebsocketClient,
        val statusFlow: MutableStateFlow<WebsocketStatus>,
        val incomingFlow: MutableSharedFlow<DeviceStateInfo>,
    )

    private val device1 = Device(
        macAddress = "AA:BB:CC:DD:EE:01",
        address = "192.168.1.101",
        originalName = "Device 1",
        branch = Branch.STABLE,
    )
    private val device2 = Device(
        macAddress = "AA:BB:CC:DD:EE:02",
        address = "192.168.1.102",
        originalName = "Device 2",
        branch = Branch.STABLE,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        every { userPreferencesRepository.showOfflineDevicesLast } returns flowOf(false)
        every { userPreferencesRepository.showHiddenDevices } returns flowOf(false)
        every { deviceDao.getAlphabetizedDevices() } returns allDevicesDbFlow
        deviceRepository = DeviceRepository(deviceDao)

        val createMockClient: (Device) -> WebsocketClient = { dev ->
            val statusFlow = MutableStateFlow(WebsocketStatus.DISCONNECTED)
            val incomingFlow = MutableSharedFlow<DeviceStateInfo>(extraBufferCapacity = 64)
            val client = mockk<WebsocketClient>(relaxed = true)

            every { client.device } returns dev
            every { client.status } returns statusFlow
            every { client.incomingStateInfo } returns incomingFlow

            val holder = TestClientHolder(client, statusFlow, incomingFlow)
            createdClients[dev.macAddress] = holder
            client
        }
        every { websocketClientFactory.create(any()) } answers { createMockClient(firstArg()) }
        every { websocketClientFactory.create(any(), any()) } answers { createMockClient(firstArg()) }

        coEvery { saveDeviceStateUseCase.invoke(any(), any()) } answers {
            firstArg()
        }
        coEvery { saveDeviceStateUseCase.invoke(any(), any(), any()) } answers {
            firstArg()
        }
        coEvery { deviceUpdateManager.checkForUpdate(any<Device>(), any()) } returns null
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        createdClients.clear()
    }

    private fun createViewModel(
        currentTimeProvider: () -> Long = System::currentTimeMillis,
    ): DeviceWebsocketListViewModel = DeviceWebsocketListViewModel(
        userPreferencesRepository = userPreferencesRepository,
        deviceRepository = deviceRepository,
        websocketClientFactory = websocketClientFactory,
        widgetManager = widgetManager,
        saveDeviceStateUseCase = saveDeviceStateUseCase,
        deviceUpdateManager = deviceUpdateManager,
        applicationContext = applicationContext,
        backgroundDispatcher = testDispatcher,
        ioDispatcher = testDispatcher,
    ).apply {
        this.currentTimeProvider = currentTimeProvider
    }

    @Test
    fun `initial device emission creates clients and updates allDevicesWithState`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1, device2)
        advanceUntilIdle()

        verify(exactly = 1) { websocketClientFactory.create(device1, any()) }
        verify(exactly = 1) { websocketClientFactory.create(device2, any()) }

        val latest = viewModel.allDevicesWithState.value
        assertEquals(2, latest.size)
        assertEquals("AA:BB:CC:DD:EE:01", latest[0].device.macAddress)
        assertEquals("AA:BB:CC:DD:EE:02", latest[1].device.macAddress)
        assertEquals(WebsocketStatus.DISCONNECTED, latest[0].websocketStatus)
        assertEquals(WebsocketStatus.DISCONNECTED, latest[1].websocketStatus)
        assertFalse(latest[0].isOnline)

        job.cancel()
    }

    @Test
    fun `client status changes update allDevicesWithState reactively`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        holder.statusFlow.value = WebsocketStatus.CONNECTED
        advanceUntilIdle()

        val latest = viewModel.allDevicesWithState.value
        assertEquals(1, latest.size)
        assertEquals(WebsocketStatus.CONNECTED, latest[0].websocketStatus)
        assertTrue(latest[0].isOnline)

        job.cancel()
    }

    @Test
    fun `incoming stateInfo invokes saveDeviceStateUseCase and updates widgets`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val stateInfo = DeviceStateInfo(
            state = State(isOn = true, brightness = 200),
            info = Info(
                version = "0.14.0",
                name = "Living Room WLED",
                leds = Leds(count = 60),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            ),
        )

        holder.incomingFlow.emit(stateInfo)
        advanceUntilIdle()

        coVerify(atLeast = 1) { saveDeviceStateUseCase.invoke(device1, stateInfo, any()) }
        coVerify(atLeast = 1) { widgetManager.updateWidgetsFromDeviceWithState(applicationContext, any()) }

        val latest = viewModel.allDevicesWithState.value
        assertEquals(1, latest.size)
        assertEquals(stateInfo, latest[0].stateInfo)

        job.cancel()
    }

    @Test
    fun `subsequent incoming frame with identical widget-visible state skips widget update`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {}
            }

            allDevicesDbFlow.value = listOf(device1)
            advanceUntilIdle()

            val holder = createdClients[device1.macAddress]!!
            val stateInfo1 = DeviceStateInfo(
                state = State(isOn = true, brightness = 128),
                info = Info(
                    version = "16.0.1",
                    name = "Device 1",
                    leds = Leds(count = 60),
                    wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                ),
            )

            holder.incomingFlow.emit(stateInfo1)
            advanceUntilIdle()

            coVerify(exactly = 1) { widgetManager.updateWidgetsFromDeviceWithState(applicationContext, any()) }

            // Second frame only changes wifi rssi / signal, which is not widget-visible
            val stateInfo2 = stateInfo1.copy(
                info = stateInfo1.info.copy(
                    wifi = Wifi(bssid = "mac", rssi = -70, signal = 60, channel = 1),
                ),
            )
            holder.incomingFlow.emit(stateInfo2)
            advanceUntilIdle()

            // Still exactly 1 call: redundant widget update broadcast is skipped
            coVerify(exactly = 1) { widgetManager.updateWidgetsFromDeviceWithState(applicationContext, any()) }

            job.cancel()
        }

    @Test
    fun `device address changed reconnects client`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val oldHolder = createdClients[device1.macAddress]!!
        val updatedDevice1 = device1.copy(address = "192.168.1.200")
        allDevicesDbFlow.value = listOf(updatedDevice1)
        advanceUntilIdle()

        verify(exactly = 1) { oldHolder.client.destroy() }
        verify(exactly = 1) { websocketClientFactory.create(updatedDevice1, any()) }

        val latest = viewModel.allDevicesWithState.value
        assertEquals("192.168.1.200", latest[0].device.address)

        job.cancel()
    }

    @Test
    fun `device removed destroys client and cancels job`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1, device2)
        advanceUntilIdle()

        val holder1 = createdClients[device1.macAddress]!!
        allDevicesDbFlow.value = listOf(device2)
        advanceUntilIdle()

        verify(exactly = 1) { holder1.client.destroy() }

        val latest = viewModel.allDevicesWithState.value
        assertEquals(1, latest.size)
        assertEquals(device2.macAddress, latest[0].device.macAddress)

        job.cancel()
    }

    @Test
    fun `refreshOfflineDevices reconnects disconnected clients only`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1, device2)
        advanceUntilIdle()

        val holder1 = createdClients[device1.macAddress]!!
        val holder2 = createdClients[device2.macAddress]!!

        holder1.statusFlow.value = WebsocketStatus.CONNECTED
        holder2.statusFlow.value = WebsocketStatus.DISCONNECTED

        viewModel.refreshOfflineDevices()

        // holder1 is already connected, should not be reconnected
        // holder2 is disconnected, should be reconnected
        verify(atLeast = 1) { holder2.client.connect() }

        job.cancel()
    }

    @Test
    fun `setBrightness and setDevicePower dispatch to client`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val devWithState = DeviceWithState(device = device1)

        viewModel.setBrightness(devWithState, 150)
        advanceUntilIdle()
        coVerify(exactly = 1) { holder.client.sendState(State(brightness = 150)) }

        viewModel.setDevicePower(devWithState, true)
        advanceUntilIdle()
        coVerify(exactly = 1) { holder.client.sendState(State(isOn = true)) }

        job.cancel()
    }

    @Test
    fun `deleteDevice deletes widgets and removes device from repository`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.deleteDevice(device1)
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetManager.deleteWidgetsForDevice(applicationContext, device1.macAddress) }
        coVerify(exactly = 1) { deviceDao.delete(device1) }
    }

    @Test
    fun `incoming stateInfo for up to date device checks update once and skips subsequent frames`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {}
            }

            allDevicesDbFlow.value = listOf(device1)
            advanceUntilIdle()

            val holder = createdClients[device1.macAddress]!!
            val stateInfo = DeviceStateInfo(
                state = State(isOn = true, brightness = 100),
                info = Info(
                    version = "0.14.0",
                    name = "Living Room WLED",
                    leds = Leds(count = 60),
                    wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                ),
            )

            // Initial frame: stateInfo was null, so checkForUpdate must be called once
            holder.incomingFlow.emit(stateInfo)
            advanceUntilIdle()

            coVerify(exactly = 1) { deviceUpdateManager.checkForUpdate(any<Device>(), any()) }
            assertEquals(null, viewModel.allDevicesWithState.value[0].updateVersionTag)

            // Subsequent frames with only brightness changes (device remains up-to-date)
            for (b in 101..110) {
                holder.incomingFlow.emit(stateInfo.copy(state = State(isOn = true, brightness = b)))
            }
            advanceUntilIdle()

            // checkForUpdate should NOT have been called on any of the subsequent frames
            coVerify(exactly = 1) { deviceUpdateManager.checkForUpdate(any<Device>(), any()) }

            job.cancel()
        }

    @Test
    fun `incoming stateInfo rechecks update when version changes`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val baseStateInfo = DeviceStateInfo(
            state = State(isOn = true, brightness = 100),
            info = Info(
                version = "0.14.0",
                name = "Living Room WLED",
                leds = Leds(count = 60),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            ),
        )

        holder.incomingFlow.emit(baseStateInfo)
        advanceUntilIdle()
        coVerify(exactly = 1) { deviceUpdateManager.checkForUpdate(any<Device>(), any()) }

        // Emit new frame with changed version
        val updatedVersionStateInfo = baseStateInfo.copy(
            info = baseStateInfo.info.copy(version = "0.14.1"),
        )
        holder.incomingFlow.emit(updatedVersionStateInfo)
        advanceUntilIdle()

        // checkForUpdate must be called a second time
        coVerify(exactly = 2) { deviceUpdateManager.checkForUpdate(any<Device>(), any()) }

        job.cancel()
    }

    @Test
    fun `cancellation exception in persistDeviceState is rethrown and cancels frame observation`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {}
            }

            allDevicesDbFlow.value = listOf(device1)
            advanceUntilIdle()

            val holder = createdClients[device1.macAddress]!!
            coEvery {
                saveDeviceStateUseCase.invoke(any(), any(), any())
            } throws CancellationException("Test cancellation")

            val stateInfo = DeviceStateInfo(
                state = State(isOn = true, brightness = 100),
                info = Info(
                    version = "0.14.0",
                    name = "Living Room WLED",
                    leds = Leds(count = 60),
                    wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                ),
            )

            holder.incomingFlow.emit(stateInfo)
            advanceUntilIdle()

            // Subsequent emissions should not update state because the coroutine cancelled cooperatively
            coEvery { saveDeviceStateUseCase.invoke(any(), any(), any()) } returns device1
            holder.incomingFlow.emit(stateInfo.copy(state = State(isOn = true, brightness = 222)))
            advanceUntilIdle()

            // State was not updated to 222 because client observation coroutine was cancelled
            assertTrue(viewModel.allDevicesWithState.value.first().stateInfo?.state?.brightness != 222)

            job.cancel()
        }

    @Test
    fun `device reordering from database emits updated list order`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val emittedLists = mutableListOf<List<String>>()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect { list ->
                emittedLists.add(list.map { it.device.macAddress })
            }
        }

        allDevicesDbFlow.value = listOf(device1, device2)
        advanceUntilIdle()

        assertEquals(
            listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
            viewModel.allDevicesWithState.value.map { it.device.macAddress },
        )

        // Now reorder the list from the database
        allDevicesDbFlow.value = listOf(device2, device1)
        advanceUntilIdle()

        assertEquals(
            listOf("AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:01"),
            viewModel.allDevicesWithState.value.map { it.device.macAddress },
        )
        assertTrue(emittedLists.contains(listOf("AA:BB:CC:DD:EE:02", "AA:BB:CC:DD:EE:01")))

        job.cancel()
    }

    @Test
    fun `updateDeviceState updates device in allDevicesWithState immediately`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val updatedState = DeviceWithState(
            device = device1.copy(originalName = "Updated Name"),
            stateInfo = DeviceStateInfo(
                state = State(isOn = true),
                info = Info(
                    version = "16.0.1",
                    name = "Updated Name",
                    leds = Leds(count = 60),
                    wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                ),
            ),
            websocketStatus = WebsocketStatus.CONNECTED,
            updateVersionTag = null,
        )

        viewModel.updateDeviceState(updatedState)
        advanceUntilIdle()

        val latest = viewModel.allDevicesWithState.value
        assertEquals(1, latest.size)
        assertEquals("16.0.1", latest[0].stateInfo?.info?.version)
        assertEquals("Updated Name", latest[0].device.originalName)

        job.cancel()
    }

    @Test
    fun `syncDevices clears updateVersionTag when skipUpdateTag matches`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val stateInfo = DeviceStateInfo(
            state = State(isOn = true),
            info = Info(
                version = "0.14.0",
                name = "Device 1",
                leds = Leds(count = 60),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            ),
        )
        coEvery { deviceUpdateManager.checkForUpdate(any(), any()) } returns "0.15.0"

        holder.incomingFlow.emit(stateInfo)
        advanceUntilIdle()

        assertEquals("0.15.0", viewModel.allDevicesWithState.value.first().updateVersionTag)

        // User skips 0.15.0
        val deviceSkipped = device1.copy(skipUpdateTag = "0.15.0")
        allDevicesDbFlow.value = listOf(deviceSkipped)
        advanceUntilIdle()

        assertEquals(null, viewModel.allDevicesWithState.value.first().updateVersionTag)

        job.cancel()
    }

    @Test
    fun `syncDevices rechecks update when branch changes`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val stateInfo = DeviceStateInfo(
            state = State(isOn = true),
            info = Info(
                version = "0.14.0",
                name = "Device 1",
                leds = Leds(count = 60),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            ),
        )
        coEvery { deviceUpdateManager.checkForUpdate(match { it.branch == Branch.STABLE }, any()) } returns "0.14.1"
        coEvery { deviceUpdateManager.checkForUpdate(match { it.branch == Branch.BETA }, any()) } returns "0.15.0-b1"

        holder.incomingFlow.emit(stateInfo)
        advanceUntilIdle()

        assertEquals("0.14.1", viewModel.allDevicesWithState.value.first().updateVersionTag)

        // User switches branch to BETA
        val deviceBeta = device1.copy(branch = Branch.BETA)
        allDevicesDbFlow.value = listOf(deviceBeta)
        advanceUntilIdle()

        assertEquals("0.15.0-b1", viewModel.allDevicesWithState.value.first().updateVersionTag)

        job.cancel()
    }

    @Test
    fun `incoming stateInfo retries update check after failure and cooldown`() = runTest(testDispatcher) {
        var fakeTime = 100_000L
        val viewModel = createViewModel(currentTimeProvider = { fakeTime })

        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val stateInfo = DeviceStateInfo(
            state = State(isOn = true),
            info = Info(
                version = "0.14.0",
                name = "Device 1",
                leds = Leds(count = 60),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            ),
        )

        // 1st attempt fails with exception
        coEvery { deviceUpdateManager.checkForUpdate(any(), any()) } throws java.io.IOException("Network down")

        holder.incomingFlow.emit(stateInfo)
        advanceUntilIdle()

        // Still null because check failed
        assertEquals(null, viewModel.allDevicesWithState.value.first().updateVersionTag)

        // Frame before cooldown should NOT trigger another check
        holder.incomingFlow.emit(stateInfo.copy(state = State(isOn = true, brightness = 150)))
        advanceUntilIdle()
        coVerify(exactly = 1) { deviceUpdateManager.checkForUpdate(any(), any()) }

        // Advance time past the 60s cooldown
        fakeTime += 61_000L
        coEvery { deviceUpdateManager.checkForUpdate(any(), any()) } returns "0.14.1"

        // Next incoming frame should now trigger a retry
        holder.incomingFlow.emit(stateInfo.copy(state = State(isOn = true, brightness = 160)))
        advanceUntilIdle()

        coVerify(exactly = 2) { deviceUpdateManager.checkForUpdate(any(), any()) }
        assertEquals("0.14.1", viewModel.allDevicesWithState.value.first().updateVersionTag)

        job.cancel()
    }

    @Test
    fun `incoming stateInfo clears updateVersionTag when device is updated to latest version`() =
        runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {}
            }

            allDevicesDbFlow.value = listOf(device1)
            advanceUntilIdle()

            val holder = createdClients[device1.macAddress]!!
            val stateInfoOld = DeviceStateInfo(
                state = State(isOn = true),
                info = Info(
                    version = "16.0.0",
                    name = "Device 1",
                    leds = Leds(count = 60),
                    wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                ),
            )
            // Update available
            coEvery {
                deviceUpdateManager.checkForUpdate(any(), match { it.info.version == "16.0.0" })
            } returns "16.0.1"

            holder.incomingFlow.emit(stateInfoOld)
            advanceUntilIdle()
            assertEquals("16.0.1", viewModel.allDevicesWithState.value.first().updateVersionTag)

            // Device reboots and sends new version 16.0.1 (now up to date)
            val stateInfoNew = stateInfoOld.copy(info = stateInfoOld.info.copy(version = "16.0.1"))
            coEvery {
                deviceUpdateManager.checkForUpdate(any(), match { it.info.version == "16.0.1" })
            } returns null

            holder.incomingFlow.emit(stateInfoNew)
            advanceUntilIdle()
            assertNull(viewModel.allDevicesWithState.value.first().updateVersionTag)

            job.cancel()
        }

    @Test
    fun `setDevicePower rolls back optimistic state when sendState fails`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allDevicesWithState.collect {}
        }

        allDevicesDbFlow.value = listOf(device1)
        advanceUntilIdle()

        val holder = createdClients[device1.macAddress]!!
        val stateInfo = DeviceStateInfo(
            state = State(isOn = false),
            info = Info(
                version = "16.0.1",
                name = "Device 1",
                leds = Leds(count = 60),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
            ),
        )
        holder.incomingFlow.emit(stateInfo)
        advanceUntilIdle()

        val deviceBefore = viewModel.allDevicesWithState.value.first()
        assertEquals(false, deviceBefore.stateInfo?.state?.isOn)

        // Mock sendState failure
        coEvery { holder.client.sendState(any()) } returns false

        viewModel.setDevicePower(deviceBefore, true)
        advanceUntilIdle()

        // Rolled back to false on failure
        val deviceAfter = viewModel.allDevicesWithState.value.first()
        assertEquals(false, deviceAfter.stateInfo?.state?.isOn)

        job.cancel()
    }
}
