package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceWebsocketListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val userPreferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val deviceConnectionManager: DeviceConnectionManager = mockk(relaxed = true)

    private val allDevicesWithStateFlow = MutableStateFlow<List<DeviceWithState>>(emptyList())
    private val isPausedFlow = MutableStateFlow(false)

    private val sampleDevice = Device(
        macAddress = "AA:BB:CC:DD:EE:01",
        address = "192.168.1.101",
        originalName = "Test Light",
    )
    private val sampleDeviceWithState = DeviceWithState(
        device = sampleDevice,
        websocketStatus = WebsocketStatus.CONNECTED,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { userPreferencesRepository.showOfflineDevicesLast } returns flowOf(true)
        every { userPreferencesRepository.showHiddenDevices } returns flowOf(false)
        every { deviceConnectionManager.allDevicesWithState } returns allDevicesWithStateFlow
        every { deviceConnectionManager.isPaused } returns isPausedFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DeviceWebsocketListViewModel = DeviceWebsocketListViewModel(
        userPreferencesRepository = userPreferencesRepository,
        deviceConnectionManager = deviceConnectionManager,
    )

    @Test
    fun `allDevicesWithState reflects connection manager flow`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        allDevicesWithStateFlow.value = listOf(sampleDeviceWithState)

        assertEquals(listOf(sampleDeviceWithState), viewModel.allDevicesWithState.value)
    }

    @Test
    fun `isPaused reflects connection manager flow`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        isPausedFlow.value = true

        assertTrue(viewModel.isPaused.value)
    }

    @Test
    fun `setBrightness delegates to connection manager`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { deviceConnectionManager.setBrightness("AA:BB:CC:DD:EE:01", 200) } returns true

        viewModel.setBrightness(sampleDeviceWithState, 200)
        advanceUntilIdle()

        coVerify { deviceConnectionManager.setBrightness("AA:BB:CC:DD:EE:01", 200) }
    }

    @Test
    fun `setDevicePower delegates to connection manager`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { deviceConnectionManager.setPower("AA:BB:CC:DD:EE:01", true) } returns true

        viewModel.setDevicePower(sampleDeviceWithState, true)
        advanceUntilIdle()

        coVerify { deviceConnectionManager.setPower("AA:BB:CC:DD:EE:01", true) }
    }

    @Test
    fun `refreshOfflineDevices delegates to connection manager`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.refreshOfflineDevices()

        verify { deviceConnectionManager.refreshOfflineDevices() }
    }

    @Test
    fun `deleteDevice delegates to connection manager`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        coEvery { deviceConnectionManager.deleteDevice(sampleDevice) } returns Unit

        viewModel.deleteDevice(sampleDevice)
        advanceUntilIdle()

        coVerify { deviceConnectionManager.deleteDevice(sampleDevice) }
    }

    @Test
    fun `updateDeviceState delegates to connection manager`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.updateDeviceState(sampleDeviceWithState)

        verify { deviceConnectionManager.updateDeviceState(sampleDeviceWithState) }
    }
}
