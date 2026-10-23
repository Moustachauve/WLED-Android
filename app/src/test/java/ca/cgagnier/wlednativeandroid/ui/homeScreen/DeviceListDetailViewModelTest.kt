package ca.cgagnier.wlednativeandroid.ui.homeScreen

import androidx.lifecycle.LifecycleOwner
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.DiscoveredDevice
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val autoDiscoveryFlow = MutableStateFlow(true)
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true) {
        every { showHiddenDevices } returns flowOf(false)
        every { autoDiscovery } returns autoDiscoveryFlow
    }
    private val lifecycleOwner: LifecycleOwner = mockk(relaxed = true)
    private val networkManager: NetworkConnectivityManager = mockk(relaxed = true)
    private val deviceFirstContactService: DeviceFirstContactService = mockk(relaxed = true)
    private val discoveredDevicesFlow = MutableSharedFlow<DiscoveredDevice>(extraBufferCapacity = 64)
    private val discoveryService: DeviceDiscovery = mockk(relaxed = true) {
        every { discoveredDevices } returns discoveredDevicesFlow
    }

    private lateinit var viewModel: DeviceListDetailViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DeviceListDetailViewModel(
            preferencesRepository = preferencesRepository,
            networkManager = networkManager,
            deviceFirstContactService = deviceFirstContactService,
            discoveryService = discoveryService,
            ioDispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun discoveredDeviceForwardsToFirstContactService() = runTest(testDispatcher) {
        val device = DiscoveredDevice(address = "192.168.1.50", macAddress = "AABBCCDDEEFF")

        discoveredDevicesFlow.emit(device)
        advanceUntilIdle()

        coVerify(exactly = 1) {
            deviceFirstContactService.processDiscoveredDevice(device)
        }
    }

    @Test
    fun startDiscoveryServiceTimedCancelsPreviousTimerBeforeStopping() = runTest(testDispatcher) {
        viewModel.startDiscoveryServiceTimed(timeMillis = 10_000L)
        runCurrent()
        verify(exactly = 1) { discoveryService.start() }

        advanceTimeBy(6_000L)
        viewModel.startDiscoveryServiceTimed(timeMillis = 10_000L)
        runCurrent()

        // Advance past the first timer's 10s mark; discovery must still be running
        advanceTimeBy(5_000L)
        verify(exactly = 0) { discoveryService.stop() }

        // Advance to complete the second 10s timer
        advanceTimeBy(5_000L)
        runCurrent()
        verify(exactly = 1) { discoveryService.stop() }
    }

    @Test
    fun onResumeStartsDiscoveryWhenAutoDiscoveryEnabled() = runTest(testDispatcher) {
        runCurrent()

        viewModel.onResume(lifecycleOwner)
        runCurrent()

        verify(exactly = 1) { discoveryService.start() }
    }

    @Test
    fun onResumeSkipsDiscoveryWhenAutoDiscoveryDisabled() = runTest(testDispatcher) {
        autoDiscoveryFlow.value = false
        runCurrent()

        viewModel.onResume(lifecycleOwner)
        runCurrent()

        verify(exactly = 0) { discoveryService.start() }
    }

    @Test
    fun manualRefreshStartsDiscoveryWhenAutoDiscoveryDisabled() = runTest(testDispatcher) {
        autoDiscoveryFlow.value = false
        runCurrent()

        viewModel.startDiscoveryServiceTimed()
        runCurrent()

        verify(exactly = 1) { discoveryService.start() }
    }

    @Test
    fun onPauseBeforeAutoDiscoveryStartsPreventsDiscovery() = runTest(testDispatcher) {
        runCurrent()

        viewModel.onResume(lifecycleOwner)
        viewModel.onPause(lifecycleOwner)
        runCurrent()

        verify(exactly = 0) { discoveryService.start() }
    }

    @Test
    fun enablingAutoDiscoveryStartsDiscovery() = runTest(testDispatcher) {
        autoDiscoveryFlow.value = false
        runCurrent()

        autoDiscoveryFlow.value = true
        runCurrent()

        verify(exactly = 1) { discoveryService.start() }
    }

    @Test
    fun disablingAutoDiscoveryStopsDiscovery() = runTest(testDispatcher) {
        runCurrent()

        autoDiscoveryFlow.value = false
        runCurrent()

        verify(exactly = 1) { discoveryService.stop() }
    }
}
