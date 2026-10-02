package ca.cgagnier.wlednativeandroid.ui.homeScreen

import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.DiscoveredDevice
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceListDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true) {
        every { showHiddenDevices } returns flowOf(false)
    }
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
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun discoveredDeviceWithMacAddressUpdatesAddressWhenDeviceExists() = runTest {
        coEvery { deviceFirstContactService.tryUpdateAddress("AABBCCDDEEFF", "192.168.1.50") } returns true

        discoveredDevicesFlow.emit(DiscoveredDevice(address = "192.168.1.50", macAddress = "AABBCCDDEEFF"))
        advanceUntilIdle()

        coVerify(timeout = 2000) {
            deviceFirstContactService.tryUpdateAddress("AABBCCDDEEFF", "192.168.1.50")
        }
        coVerify(exactly = 0) {
            deviceFirstContactService.fetchAndUpsertDevice(any())
        }
    }

    @Test
    fun discoveredDeviceFallsBackToFetchAndUpsertWhenAddressUpdateFails() = runTest {
        coEvery { deviceFirstContactService.tryUpdateAddress(any(), any()) } returns false

        discoveredDevicesFlow.emit(DiscoveredDevice(address = "192.168.1.50", macAddress = "AABBCCDDEEFF"))
        advanceUntilIdle()

        coVerify(timeout = 2000) {
            deviceFirstContactService.fetchAndUpsertDevice("192.168.1.50")
        }
    }

    @Test
    fun discoveredDeviceWithoutMacAddressFallsBackToFetchAndUpsert() = runTest {
        coEvery { deviceFirstContactService.tryUpdateAddress(null, "192.168.1.50") } returns false

        discoveredDevicesFlow.emit(DiscoveredDevice(address = "192.168.1.50", macAddress = null))
        advanceUntilIdle()

        coVerify(timeout = 2000) {
            deviceFirstContactService.fetchAndUpsertDevice("192.168.1.50")
        }
    }
}
