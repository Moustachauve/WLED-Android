package ca.cgagnier.wlednativeandroid.service

import android.content.Context
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.WidgetSnapshot
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WidgetStateSyncTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val context: Context = mockk(relaxed = true)
    private val connectionManager: DeviceConnectionManager = mockk(relaxed = true)
    private val widgetManager: WledWidgetManager = mockk(relaxed = true)
    private val devicesFlow = MutableStateFlow<List<DeviceWithState>>(emptyList())
    private var currentTime = 1_000_000L

    @BeforeEach
    fun setUp() {
        every { context.getString(R.string.default_device_name) } returns "Default WLED"
        every { connectionManager.allDevicesWithState } returns devicesFlow
    }

    private fun TestScope.createAndStartSync(): WidgetStateSync = WidgetStateSync(
        context = context,
        connectionManager = connectionManager,
        widgetManager = widgetManager,
        coroutineScope = backgroundScope,
        ioDispatcher = testDispatcher,
        timeProvider = { currentTime },
    ).also { it.start() }

    @Suppress("LongParameterList")
    private fun createDeviceWithState(
        macAddress: String = "AA:BB:CC:DD:EE:01",
        address: String = "192.168.1.101",
        customName: String = "Desk Strip",
        isOn: Boolean = true,
        brightness: Int = 128,
        colors: List<List<Int>>? = listOf(listOf(255, 0, 0)),
        status: WebsocketStatus = WebsocketStatus.CONNECTED,
        hasStateInfo: Boolean = true,
    ): DeviceWithState {
        val stateInfo = if (hasStateInfo) {
            DeviceStateInfo(
                state = State(
                    isOn = isOn,
                    brightness = brightness,
                    segment = colors?.let { listOf(Segment(colors = it)) },
                ),
                info = Info(
                    version = "0.14.0",
                    leds = Leds(count = 30),
                    wifi = Wifi(),
                    name = "WLED",
                ),
            )
        } else {
            null
        }
        return DeviceWithState(
            device = Device(
                macAddress = macAddress,
                address = address,
                customName = customName,
                originalName = "WLED",
            ),
            stateInfo = stateInfo,
            websocketStatus = status,
        )
    }

    @Test
    fun `updates widget when device state first arrives`() = runTest(testDispatcher) {
        createAndStartSync()
        val device = createDeviceWithState(isOn = true)

        devicesFlow.value = listOf(device)
        advanceUntilIdle()

        val snapshotSlot = slot<WidgetSnapshot>()
        coVerify(exactly = 1) { widgetManager.updateWidgetsFromSnapshot(context, capture(snapshotSlot)) }
        assertEquals("AA:BB:CC:DD:EE:01", snapshotSlot.captured.macAddress)
        assertEquals("Desk Strip", snapshotSlot.captured.name)
        assertTrue(snapshotSlot.captured.isOn)
        assertTrue(snapshotSlot.captured.isOnline)
    }

    @Test
    fun `skips widget update when only brightness changes within refresh interval`() = runTest(testDispatcher) {
        createAndStartSync()
        val initial = createDeviceWithState(brightness = 100)
        devicesFlow.value = listOf(initial)
        advanceUntilIdle()

        currentTime += 5_000L
        val brightnessChanged = createDeviceWithState(brightness = 200)
        devicesFlow.value = listOf(brightnessChanged)
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetManager.updateWidgetsFromSnapshot(context, any()) }
    }

    @Test
    fun `refreshes widget timer when refresh interval elapses without visible changes`() = runTest(testDispatcher) {
        createAndStartSync()
        val initial = createDeviceWithState(brightness = 100)
        devicesFlow.value = listOf(initial)
        advanceUntilIdle()

        currentTime += 30_000L
        val nextFrame = createDeviceWithState(brightness = 101)
        devicesFlow.value = listOf(nextFrame)
        advanceUntilIdle()

        val snapshots = mutableListOf<WidgetSnapshot>()
        coVerify(exactly = 2) { widgetManager.updateWidgetsFromSnapshot(context, capture(snapshots)) }
        assertEquals(1_000_000L, snapshots[0].lastUpdated)
        assertEquals(1_030_000L, snapshots[1].lastUpdated)
    }

    @Test
    fun `does not mark widget offline on disconnect and refreshes when reconnected`() = runTest(testDispatcher) {
        createAndStartSync()
        val online = createDeviceWithState(status = WebsocketStatus.CONNECTED)
        devicesFlow.value = listOf(online)
        advanceUntilIdle()

        val disconnected = createDeviceWithState(status = WebsocketStatus.DISCONNECTED)
        devicesFlow.value = listOf(disconnected)
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetManager.updateWidgetsFromSnapshot(context, any()) }

        currentTime += 1_000L
        val reconnected = createDeviceWithState(status = WebsocketStatus.CONNECTED)
        devicesFlow.value = listOf(reconnected)
        advanceUntilIdle()

        val snapshots = mutableListOf<WidgetSnapshot>()
        coVerify(exactly = 2) { widgetManager.updateWidgetsFromSnapshot(context, capture(snapshots)) }
        assertTrue(snapshots.all { it.isOnline })
    }

    @Test
    fun `re-pushes widget state after stop and start`() = runTest(testDispatcher) {
        val sync = createAndStartSync()
        val online = createDeviceWithState(status = WebsocketStatus.CONNECTED)
        devicesFlow.value = listOf(online)
        advanceUntilIdle()

        sync.stop()
        currentTime += 2_000L
        sync.start()
        advanceUntilIdle()

        coVerify(exactly = 2) { widgetManager.updateWidgetsFromSnapshot(context, any()) }
    }

    @Test
    fun `deletes widgets when online device is removed`() = runTest(testDispatcher) {
        createAndStartSync()
        val device = createDeviceWithState(macAddress = "AA:BB:CC:DD:EE:01")
        devicesFlow.value = listOf(device)
        advanceUntilIdle()

        devicesFlow.value = emptyList()
        advanceUntilIdle()

        coVerify(exactly = 1) { widgetManager.deleteWidgetsForDevice(context, "AA:BB:CC:DD:EE:01") }
    }

    @Test
    fun `deletes widgets when offline device without stateInfo is removed`() = runTest(testDispatcher) {
        createAndStartSync()
        val offlineDevice = createDeviceWithState(
            macAddress = "AA:BB:CC:DD:EE:02",
            status = WebsocketStatus.DISCONNECTED,
            hasStateInfo = false,
        )
        devicesFlow.value = listOf(offlineDevice)
        advanceUntilIdle()

        devicesFlow.value = emptyList()
        advanceUntilIdle()

        coVerify(exactly = 0) { widgetManager.updateWidgetsFromSnapshot(context, any()) }
        coVerify(exactly = 1) { widgetManager.deleteWidgetsForDevice(context, "AA:BB:CC:DD:EE:02") }
    }
}
