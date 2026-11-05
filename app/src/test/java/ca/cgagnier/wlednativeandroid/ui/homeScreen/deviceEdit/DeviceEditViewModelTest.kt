package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.content.Context
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [DeviceEditViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val deviceRepository: DeviceRepository = mockk(relaxed = true)
    private val repositoryDao: RepositoryDao = mockk(relaxed = true)
    private val versionWithAssetsRepository: VersionWithAssetsRepository = mockk(relaxed = true)
    private val githubApi: GithubApi = mockk(relaxed = true)
    private val releaseService: ReleaseService = mockk(relaxed = true)
    private val deviceConnectionManager: DeviceConnectionManager = mockk(relaxed = true)
    private val widgetManager: WledWidgetManager = mockk(relaxed = true)
    private val applicationContext: Context = mockk(relaxed = true)
    private lateinit var viewModel: DeviceEditViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        viewModel = DeviceEditViewModel(
            deviceRepository = deviceRepository,
            repositoryDao = repositoryDao,
            versionWithAssetsRepository = versionWithAssetsRepository,
            githubApi = githubApi,
            releaseService = releaseService,
            deviceConnectionManager = deviceConnectionManager,
            widgetManager = widgetManager,
            applicationContext = applicationContext,
            ioDispatcher = testDispatcher,
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Helpers ---------------------------------------------------------------

    private val sampleDevice = Device(
        macAddress = "AA:BB:CC:DD:EE:01",
        address = "192.168.0.1",
        originalName = "Office Light",
    )

    private fun makeDeviceWithState(version: String = "0.15.0"): DeviceWithState = DeviceWithState(
        device = sampleDevice,
        stateInfo = DeviceStateInfo(
            state = State(isOn = true, brightness = 200, transition = 7),
            info = Info(
                leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
                wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                name = "WLED",
                version = version,
            ),
        ),
    )

    private fun makeVersion(tagName: String = "v0.15.1"): VersionWithAssets = VersionWithAssets(
        version = Version.getPreviewVersion().copy(tagName = tagName),
        assets = emptyList(),
    )

    private fun TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    // -- Targeted repository mutations ----------------------------------------

    @Test
    fun `updateCustomName persists targeted custom name and updates widgets`() = runTest(testDispatcher) {
        viewModel.updateCustomName(sampleDevice, "Desk Strip")
        advanceUntilIdle()

        coVerify(exactly = 1) {
            deviceRepository.updateCustomName("AA:BB:CC:DD:EE:01", "Desk Strip")
            widgetManager.updateWidgetDeviceDetails(
                applicationContext,
                sampleDevice.copy(customName = "Desk Strip"),
            )
        }
    }

    @Test
    fun `updateDeviceHidden persists targeted hidden state`() = runTest(testDispatcher) {
        viewModel.updateDeviceHidden(sampleDevice, true)
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepository.updateIsHidden("AA:BB:CC:DD:EE:01", true) }
    }

    @Test
    fun `updateDeviceBranch persists targeted branch`() = runTest(testDispatcher) {
        viewModel.updateDeviceBranch(sampleDevice, Branch.BETA)
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepository.updateBranch("AA:BB:CC:DD:EE:01", Branch.BETA) }
    }

    @Test
    fun `skipUpdate persists targeted skipUpdateTag and clears details dialog`() = runTest(testDispatcher) {
        collectUiState()
        val version = makeVersion("v0.15.1")
        coEvery { versionWithAssetsRepository.getVersionByTag(1L, "v0.15.1") } returns version

        viewModel.showUpdateDetails(1L, "v0.15.1")
        advanceUntilIdle()
        assertEquals(version, viewModel.uiState.value.updateDetailsVersion)

        viewModel.skipUpdate(sampleDevice, version)
        advanceUntilIdle()

        coVerify(exactly = 1) { deviceRepository.updateSkipUpdateTag("AA:BB:CC:DD:EE:01", "v0.15.1") }
        assertNull(viewModel.uiState.value.updateDetailsVersion)
    }

    @Test
    fun `checkForUpdates clears skipUpdateTag refreshes releases and rechecks updates`() = runTest(testDispatcher) {
        collectUiState()

        viewModel.checkForUpdates(sampleDevice)
        advanceUntilIdle()

        coVerifyOrder {
            deviceRepository.updateSkipUpdateTag("AA:BB:CC:DD:EE:01", "")
            releaseService.refreshVersionsForDevice(githubApi, sampleDevice)
            deviceConnectionManager.recheckUpdates()
        }
        assertFalse(viewModel.uiState.value.isCheckingUpdates)
    }

    // -- stopUpdateInstall: connection manager delegation ---------------------

    @Test
    fun `stopUpdateInstall marks update installed on success`() {
        val deviceWithState = makeDeviceWithState("0.15.0")
        val version = makeVersion("v0.15.1")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(device = deviceWithState, wasSuccessful = true)

        verify(exactly = 1) {
            deviceConnectionManager.markUpdateInstalled("AA:BB:CC:DD:EE:01", "v0.15.1")
        }
    }

    @Test
    fun `stopUpdateInstall does not mark update installed when unsuccessful`() {
        val deviceWithState = makeDeviceWithState("0.15.0")
        val version = makeVersion("v0.15.1")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(device = deviceWithState, wasSuccessful = false)

        verify(exactly = 0) { deviceConnectionManager.markUpdateInstalled(any(), any()) }
    }

    @Test
    fun `stopUpdateInstall does not mark update installed when device is null`() {
        val version = makeVersion("v0.15.1")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(device = null, wasSuccessful = true)

        verify(exactly = 0) { deviceConnectionManager.markUpdateInstalled(any(), any()) }
    }

    // -- Dialog lifecycle methods ---------------------------------------------

    @Test
    fun `startUpdateInstall sets install version and stopUpdateInstall clears it`() = runTest(testDispatcher) {
        collectUiState()
        val version = makeVersion("v0.15.1")

        viewModel.startUpdateInstall(version)
        advanceUntilIdle()
        assertEquals(version, viewModel.uiState.value.updateInstallVersion)

        viewModel.stopUpdateInstall()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.updateInstallVersion)
    }

    @Test
    fun `showUpdateDisclaimer sets disclaimer version and hideUpdateDisclaimer clears it`() = runTest(testDispatcher) {
        collectUiState()
        val version = makeVersion("v0.15.1")

        viewModel.showUpdateDisclaimer(version)
        advanceUntilIdle()
        assertEquals(version, viewModel.uiState.value.updateDisclaimerVersion)

        viewModel.hideUpdateDisclaimer()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.updateDisclaimerVersion)
    }
}
