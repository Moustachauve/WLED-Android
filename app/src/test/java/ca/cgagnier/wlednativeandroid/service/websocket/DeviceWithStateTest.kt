package ca.cgagnier.wlednativeandroid.service.websocket

import android.content.Context
import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceDao
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

/**
 * Empirical Challenger Suite testing:
 * 1. DeviceWithState immutability, copy semantics, deep structural equality, and absence of mutable state.
 * 2. DeviceWebsocketListViewModel StateFlow emission dynamics and distinctUntilChanged suppression.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceWithStateTest {

    private fun createSampleDevice(
        mac: String = "AA:BB:CC:DD:EE:01",
        address: String = "192.168.1.101",
        customName: String = "Test Device",
    ): Device = Device(
        macAddress = mac,
        address = address,
        customName = customName,
        originalName = "WLED Light",
        branch = Branch.STABLE,
    )

    private fun createSampleStateInfo(
        isOn: Boolean = true,
        brightness: Int = 128,
        version: String = "0.14.0",
        segments: List<Segment>? = null,
    ): DeviceStateInfo = DeviceStateInfo(
        state = State(
            isOn = isOn,
            brightness = brightness,
            segment = segments,
        ),
        info = Info(
            name = "WLED Light",
            version = version,
            leds = Leds(count = 30),
            wifi = Wifi(bssid = "00:11:22:33:44:55", rssi = -60, signal = 80, channel = 6),
        ),
    )

    @Nested
    @DisplayName("DeviceWithState Immutability & Structural Equality Tests")
    inner class ImmutabilityAndStructuralEqualityTests {

        @Test
        fun `reflection audit confirms all properties are immutable vals`() {
            val kClass = DeviceWithState::class.java
            val fields = kClass.declaredFields

            assertTrue(fields.isNotEmpty(), "DeviceWithState should declare fields")
            for (field in fields) {
                if (field.isSynthetic) continue
                // All backing fields for Kotlin `val` in data classes must be private final
                assertTrue(
                    Modifier.isFinal(field.modifiers),
                    "Field ${field.name} in DeviceWithState must be final to guarantee immutability",
                )
            }
        }

        @Test
        fun `copy semantics maintain isolation without mutating original instance`() {
            val originalDevice = createSampleDevice()
            val originalStateInfo = createSampleStateInfo()
            val original = DeviceWithState(
                device = originalDevice,
                stateInfo = originalStateInfo,
                websocketStatus = WebsocketStatus.DISCONNECTED,
                updateVersionTag = null,
            )

            val updatedStatus = original.copy(websocketStatus = WebsocketStatus.CONNECTED)
            assertEquals(WebsocketStatus.DISCONNECTED, original.websocketStatus)
            assertEquals(WebsocketStatus.CONNECTED, updatedStatus.websocketStatus)
            assertFalse(original.isOnline)
            assertTrue(updatedStatus.isOnline)

            val updatedTag = original.copy(updateVersionTag = "v0.14.1")
            assertNull(original.updateVersionTag)
            assertEquals("v0.14.1", updatedTag.updateVersionTag)

            val modifiedDevice = originalDevice.copy(customName = "Living Room")
            val updatedDevice = original.copy(device = modifiedDevice)
            assertEquals("Test Device", original.device.customName)
            assertEquals("Living Room", updatedDevice.device.customName)
        }

        @Test
        fun `deep structural equality satisfies equivalence relations`() {
            val stateInfo1 = createSampleStateInfo()
            val stateInfo2 = createSampleStateInfo()
            val stateInfo3 = createSampleStateInfo()

            val d1 = DeviceWithState(
                device = createSampleDevice(),
                stateInfo = stateInfo1,
                websocketStatus = WebsocketStatus.CONNECTED,
                updateVersionTag = "v0.14.0",
            )
            val d2 = DeviceWithState(
                device = createSampleDevice(),
                stateInfo = stateInfo2,
                websocketStatus = WebsocketStatus.CONNECTED,
                updateVersionTag = "v0.14.0",
            )
            val d3 = DeviceWithState(
                device = createSampleDevice(),
                stateInfo = stateInfo3,
                websocketStatus = WebsocketStatus.CONNECTED,
                updateVersionTag = "v0.14.0",
            )

            // Reflexive
            assertEquals(d1, d1)
            assertEquals(d1.hashCode(), d1.hashCode())

            // Symmetric
            assertEquals(d1, d2)
            assertEquals(d2, d1)
            assertEquals(d1.hashCode(), d2.hashCode())

            // Transitive
            assertEquals(d2, d3)
            assertEquals(d1, d3)
        }

        @Test
        fun `structural inequality detects fine-grained changes in nested and direct fields`() {
            val base = DeviceWithState(
                device = createSampleDevice(),
                stateInfo = createSampleStateInfo(brightness = 100),
                websocketStatus = WebsocketStatus.DISCONNECTED,
                updateVersionTag = "v0.14.0",
            )

            // Direct field changes
            val diffStatus = base.copy(websocketStatus = WebsocketStatus.CONNECTING)
            assertNotEquals(base, diffStatus)
            assertNotEquals(base.hashCode(), diffStatus.hashCode())

            val diffTag = base.copy(updateVersionTag = "v0.15.0")
            assertNotEquals(base, diffTag)

            val diffStateNull = base.copy(stateInfo = null)
            assertNotEquals(base, diffStateNull)

            // Nested device field changes
            val diffDeviceMac = base.copy(device = base.device.copy(macAddress = "FF:FF:FF:FF:FF:FF"))
            assertNotEquals(base, diffDeviceMac)

            val diffDeviceName = base.copy(device = base.device.copy(customName = "Patio"))
            assertNotEquals(base, diffDeviceName)

            // Nested stateInfo field changes
            val diffBrightness = base.copy(stateInfo = createSampleStateInfo(brightness = 101))
            assertNotEquals(base, diffBrightness)

            val diffPower = base.copy(stateInfo = createSampleStateInfo(isOn = false, brightness = 100))
            assertNotEquals(base, diffPower)

            val diffVersion = base.copy(
                stateInfo = createSampleStateInfo(brightness = 100, version = "0.14.1-b1"),
            )
            assertNotEquals(base, diffVersion)

            // Nested segment collection changes
            val segA = Segment(id = 0, start = 0, stop = 10)
            val segB = Segment(id = 0, start = 0, stop = 20)
            val withSegA = base.copy(stateInfo = createSampleStateInfo(segments = listOf(segA)))
            val withSegB = base.copy(stateInfo = createSampleStateInfo(segments = listOf(segB)))
            assertNotEquals(withSegA, withSegB)
        }

        @Test
        fun `derived properties isOnline and isAPMode evaluate correctly`() {
            val regularDevice = createSampleDevice(mac = "11:22:33:44:55:66")
            val apDevice = createSampleDevice(mac = AP_MODE_MAC_ADDRESS)

            val dOnline = DeviceWithState(regularDevice, websocketStatus = WebsocketStatus.CONNECTED)
            val dConnecting = DeviceWithState(regularDevice, websocketStatus = WebsocketStatus.CONNECTING)
            val dOffline = DeviceWithState(regularDevice, websocketStatus = WebsocketStatus.DISCONNECTED)

            assertTrue(dOnline.isOnline)
            assertFalse(dConnecting.isOnline)
            assertFalse(dOffline.isOnline)

            assertFalse(dOnline.isAPMode)

            val dAp = DeviceWithState(apDevice, websocketStatus = WebsocketStatus.CONNECTED)
            assertTrue(dAp.isAPMode)
        }

        @Test
        fun `getApModeDeviceWithState returns correct defaults`() {
            val ap = getApModeDeviceWithState()
            assertEquals(AP_MODE_MAC_ADDRESS, ap.device.macAddress)
            assertEquals(DEFAULT_WLED_AP_IP, ap.device.address)
            assertEquals(WebsocketStatus.CONNECTED, ap.websocketStatus)
            assertTrue(ap.isOnline)
            assertTrue(ap.isAPMode)
            assertNull(ap.stateInfo)
        }

        @Test
        fun `updateVersionTagFlow produces current tag value`() = runTest {
            val dWithTag = DeviceWithState(createSampleDevice(), updateVersionTag = "v1.0.0")
            assertEquals("v1.0.0", dWithTag.updateVersionTagFlow.first())

            val dNoTag = DeviceWithState(createSampleDevice(), updateVersionTag = null)
            assertNull(dNoTag.updateVersionTagFlow.first())
        }
    }

    @Nested
    @DisplayName("ViewModel StateFlow Emission and distinctUntilChanged Tests")
    inner class StateFlowEmissionTests {

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

        private inner class TestClientHolder(
            val client: WebsocketClient,
            val statusFlow: MutableStateFlow<WebsocketStatus>,
            val incomingFlow: MutableSharedFlow<DeviceStateInfo>,
        )

        @BeforeEach
        fun setUp() {
            Dispatchers.setMain(testDispatcher)

            every { userPreferencesRepository.showOfflineDevicesLast } returns flowOf(false)
            every { userPreferencesRepository.showHiddenDevices } returns flowOf(false)
            every { deviceDao.getAlphabetizedDevices() } returns allDevicesDbFlow
            deviceRepository = DeviceRepository(deviceDao)

            every { websocketClientFactory.create(any()) } answers {
                val dev = firstArg<Device>()
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

            coEvery { saveDeviceStateUseCase.invoke(any(), any()) } returns null
            coEvery { saveDeviceStateUseCase.invoke(any(), any(), any()) } returns null
            coEvery { deviceUpdateManager.checkForUpdate(any<Device>(), any()) } returns null
        }

        @AfterEach
        fun tearDown() {
            Dispatchers.resetMain()
            createdClients.clear()
        }

        private fun createViewModel(): DeviceWebsocketListViewModel = DeviceWebsocketListViewModel(
            userPreferencesRepository = userPreferencesRepository,
            deviceRepository = deviceRepository,
            websocketClientFactory = websocketClientFactory,
            widgetManager = widgetManager,
            saveDeviceStateUseCase = saveDeviceStateUseCase,
            deviceUpdateManager = deviceUpdateManager,
            applicationContext = applicationContext,
        )

        @Test
        fun `duplicate DB emissions do not trigger StateFlow duplicate emissions`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val emittedLists = mutableListOf<List<DeviceWithState>>()

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {
                    emittedLists.add(it)
                }
            }

            val dev1 = createSampleDevice("AA:BB:CC:DD:EE:01", "192.168.1.101")
            val dev2 = createSampleDevice("AA:BB:CC:DD:EE:02", "192.168.1.102")

            // Initial emission from DB
            allDevicesDbFlow.value = listOf(dev1, dev2)
            advanceUntilIdle()

            val emissionsAfterFirst = emittedLists.size
            assertTrue(emissionsAfterFirst >= 1, "Should have received initial emission")
            assertEquals(2, emittedLists.last().size)

            // Re-emit the exact same list from DB
            allDevicesDbFlow.value = listOf(dev1, dev2)
            advanceUntilIdle()

            // StateFlow distinctUntilChanged must suppress duplicate emission
            assertEquals(
                emissionsAfterFirst,
                emittedLists.size,
                "StateFlow should NOT emit when DB emits identical device list",
            )

            // Now emit a changed list (dev1 customName changed)
            val updatedDev1 = dev1.copy(customName = "Kitchen Light")
            allDevicesDbFlow.value = listOf(updatedDev1, dev2)
            advanceUntilIdle()

            assertEquals(
                emissionsAfterFirst + 1,
                emittedLists.size,
                "StateFlow MUST emit when a device property is updated in DB",
            )
            assertEquals("Kitchen Light", emittedLists.last()[0].device.customName)

            job.cancel()
        }

        @Test
        fun `duplicate websocket status updates do not trigger duplicate emissions`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val emittedLists = mutableListOf<List<DeviceWithState>>()

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {
                    emittedLists.add(it)
                }
            }

            val dev1 = createSampleDevice("AA:BB:CC:DD:EE:01", "192.168.1.101")
            allDevicesDbFlow.value = listOf(dev1)
            advanceUntilIdle()

            val holder = createdClients[dev1.macAddress]!!
            val initialCount = emittedLists.size

            // Transition to CONNECTED -> should emit
            holder.statusFlow.value = WebsocketStatus.CONNECTED
            advanceUntilIdle()

            val countAfterConnected = emittedLists.size
            assertEquals(initialCount + 1, countAfterConnected)
            assertEquals(WebsocketStatus.CONNECTED, emittedLists.last()[0].websocketStatus)

            // Duplicate CONNECTED status -> should NOT emit
            holder.statusFlow.value = WebsocketStatus.CONNECTED
            advanceUntilIdle()

            assertEquals(
                countAfterConnected,
                emittedLists.size,
                "StateFlow should NOT emit when duplicate status is received",
            )

            // Transition to DISCONNECTED -> should emit
            holder.statusFlow.value = WebsocketStatus.DISCONNECTED
            advanceUntilIdle()

            assertEquals(countAfterConnected + 1, emittedLists.size)
            assertEquals(WebsocketStatus.DISCONNECTED, emittedLists.last()[0].websocketStatus)

            job.cancel()
        }

        @Test
        fun `duplicate incoming stateInfo frames do not trigger duplicate emissions`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val emittedLists = mutableListOf<List<DeviceWithState>>()

            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {
                    emittedLists.add(it)
                }
            }

            val dev1 = createSampleDevice("AA:BB:CC:DD:EE:01", "192.168.1.101")
            allDevicesDbFlow.value = listOf(dev1)
            advanceUntilIdle()

            val holder = createdClients[dev1.macAddress]!!
            val stateInfo1 = createSampleStateInfo(brightness = 100)

            // First incoming frame -> should emit
            val countBeforeFrame = emittedLists.size
            holder.incomingFlow.emit(stateInfo1)
            advanceUntilIdle()

            val countAfterFirstFrame = emittedLists.size
            assertEquals(countBeforeFrame + 1, countAfterFirstFrame)
            assertEquals(100, emittedLists.last()[0].stateInfo?.state?.brightness)

            // Second identical incoming frame -> should NOT emit
            holder.incomingFlow.emit(stateInfo1)
            advanceUntilIdle()

            assertEquals(
                countAfterFirstFrame,
                emittedLists.size,
                "StateFlow should suppress duplicate emissions when incoming stateInfo is identical",
            )

            // Third modified frame (brightness changed to 200) -> should emit
            val stateInfo2 = createSampleStateInfo(brightness = 200)
            holder.incomingFlow.emit(stateInfo2)
            advanceUntilIdle()

            assertEquals(
                countAfterFirstFrame + 1,
                emittedLists.size,
                "StateFlow MUST emit when stateInfo content changes",
            )
            assertEquals(200, emittedLists.last()[0].stateInfo?.state?.brightness)

            job.cancel()
        }

        @Test
        fun `initial DB query order and client status updates preserve order`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect {}
            }

            val dev1 = createSampleDevice("AA:01", "192.168.1.1")
            val dev2 = createSampleDevice("AA:02", "192.168.1.2")
            val dev3 = createSampleDevice("AA:03", "192.168.1.3")

            // Order: 1, 2, 3
            allDevicesDbFlow.value = listOf(dev1, dev2, dev3)
            advanceUntilIdle()

            var latest = viewModel.allDevicesWithState.value
            assertEquals(listOf("AA:01", "AA:02", "AA:03"), latest.map { it.device.macAddress })

            // State update on dev2 does not change order
            val holder2 = createdClients[dev2.macAddress]!!
            holder2.statusFlow.value = WebsocketStatus.CONNECTED
            advanceUntilIdle()

            latest = viewModel.allDevicesWithState.value
            assertEquals(listOf("AA:01", "AA:02", "AA:03"), latest.map { it.device.macAddress })
            assertEquals(WebsocketStatus.CONNECTED, latest[1].websocketStatus)

            job.cancel()
        }

        @Test
        fun `pure device reordering from DB is silently dropped due to Map equals in devicesWithStateMap`() =
            runTest(testDispatcher) {
                val viewModel = createViewModel()
                val emittedLists = mutableListOf<List<DeviceWithState>>()
                val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.allDevicesWithState.collect { emittedLists.add(it) }
                }

                val dev1 = createSampleDevice("AA:01", "192.168.1.1")
                val dev2 = createSampleDevice("AA:02", "192.168.1.2")
                val dev3 = createSampleDevice("AA:03", "192.168.1.3")

                // Initial DB emission: [1, 2, 3]
                allDevicesDbFlow.value = listOf(dev1, dev2, dev3)
                advanceUntilIdle()

                val emissionCountBefore = emittedLists.size
                assertEquals(
                    listOf("AA:01", "AA:02", "AA:03"),
                    viewModel.allDevicesWithState.value.map { it.device.macAddress },
                )

                // DB emits reordered list of identical devices: [3, 1, 2]
                allDevicesDbFlow.value = listOf(dev3, dev1, dev2)
                advanceUntilIdle()

                // Defect Demonstration:
                // devicesWithStateMap is a MutableStateFlow<Map<String, DeviceWithState>>.
                // Because Java/Kotlin Map.equals() ignores entry iteration order,
                // currentMap.equals(nextMap) is true!
                // MutableStateFlow drops the update, no emission occurs, and the order remains stale.
                assertEquals(
                    emissionCountBefore,
                    emittedLists.size,
                    "Empirical proof: StateFlow dropped emission because Map.equals() ignored order",
                )
                assertEquals(
                    listOf("AA:01", "AA:02", "AA:03"),
                    viewModel.allDevicesWithState.value.map { it.device.macAddress },
                    "Empirical proof: Order remains stale [AA:01, AA:02, AA:03] instead of [AA:03, AA:01, AA:02]",
                )

                job.cancel()
            }

        @Test
        fun `device reordering accompanied by device property update successfully updates order`() =
            runTest(testDispatcher) {
                val viewModel = createViewModel()
                val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    viewModel.allDevicesWithState.collect {}
                }

                val dev1 = createSampleDevice("AA:01", "192.168.1.1")
                val dev2 = createSampleDevice("AA:02", "192.168.1.2")
                val dev3 = createSampleDevice("AA:03", "192.168.1.3")

                allDevicesDbFlow.value = listOf(dev1, dev2, dev3)
                advanceUntilIdle()

                // When reordering is accompanied by a property change (e.g. dev1 renamed to Basement),
                // the map entries differ, so Map.equals() returns false and the new map is accepted.
                val renamedDev1 = dev1.copy(customName = "Basement")
                allDevicesDbFlow.value = listOf(dev3, renamedDev1, dev2)
                advanceUntilIdle()

                val latest = viewModel.allDevicesWithState.value
                assertEquals(listOf("AA:03", "AA:01", "AA:02"), latest.map { it.device.macAddress })
                assertEquals("Basement", latest[1].device.customName)

                job.cancel()
            }

        @Test
        fun `multiple concurrent collectors receive identical consistent state`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val collector1Emissions = mutableListOf<List<DeviceWithState>>()
            val collector2Emissions = mutableListOf<List<DeviceWithState>>()

            val job1 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect { collector1Emissions.add(it) }
            }
            val job2 = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.allDevicesWithState.collect { collector2Emissions.add(it) }
            }

            val dev1 = createSampleDevice("AA:01", "192.168.1.1")
            allDevicesDbFlow.value = listOf(dev1)
            advanceUntilIdle()

            val holder1 = createdClients[dev1.macAddress]!!
            holder1.statusFlow.value = WebsocketStatus.CONNECTED
            advanceUntilIdle()

            holder1.incomingFlow.emit(createSampleStateInfo(brightness = 50))
            advanceUntilIdle()

            holder1.incomingFlow.emit(createSampleStateInfo(brightness = 150))
            advanceUntilIdle()

            assertEquals(collector1Emissions.size, collector2Emissions.size)
            assertEquals(collector1Emissions.last(), collector2Emissions.last())
            assertEquals(150, collector1Emissions.last()[0].stateInfo?.state?.brightness)

            job1.cancel()
            job2.cancel()
        }
    }
}
