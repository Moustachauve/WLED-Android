package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.content.Context
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
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [DeviceEditViewModel].
 *
 * These tests exercise the synchronous methods of the ViewModel.
 * The combined [DeviceEditViewModel.uiState] flow is backed by
 * [SharingStarted.WhileSubscribed], so its [StateFlow.value] is
 * not reliably updated without an active collector—tests therefore
 * assert against direct side-effects (e.g. [DeviceWithState.stateInfo])
 * rather than [uiState.value].
 */
class DeviceEditViewModelTest {

    private lateinit var viewModel: DeviceEditViewModel

    @Before
    fun setup() {
        viewModel = DeviceEditViewModel(
            deviceRepository = mockk(relaxed = true),
            repositoryDao = mockk(relaxed = true),
            versionWithAssetsRepository = mockk(relaxed = true),
            githubApi = mockk(relaxed = true),
            releaseService = mockk(relaxed = true),
            widgetManager = mockk(relaxed = true),
            applicationContext = mockk<Context>(relaxed = true),
        )
    }

    // -- Helpers ---------------------------------------------------------------

    private fun makeDeviceWithState(version: String = "0.13.0"): DeviceWithState {
        val device = Device(macAddress = "mac1", address = "192.168.0.1")
        return DeviceWithState(device).apply {
            stateInfo.value = DeviceStateInfo(
                state = State(isOn = true, brightness = 200, transition = 7),
                info = Info(
                    leds = Leds(count = 30, fps = 30, maxPower = 0, maxSegment = 1),
                    wifi = Wifi(bssid = "mac", rssi = -50, signal = 100, channel = 1),
                    name = "WLED",
                    version = version,
                ),
            )
        }
    }

    private fun makeVersion(tagName: String = "v0.14.0"): VersionWithAssets = VersionWithAssets(
        version = Version.getPreviewVersion().copy(tagName = tagName),
        assets = emptyList(),
    )

    // -- stopUpdateInstall: version patching -----------------------------------

    @Test
    fun `stopUpdateInstall patches stateInfo version on success`() {
        val deviceWithState = makeDeviceWithState("0.13.0")
        val version = makeVersion("v0.14.0")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = true)

        assertEquals("0.14.0", deviceWithState.stateInfo.value?.info?.version)
    }

    @Test
    fun `stopUpdateInstall does not change stateInfo on failure`() {
        val deviceWithState = makeDeviceWithState("0.13.0")
        val version = makeVersion("v0.14.0")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = false)

        assertEquals("0.13.0", deviceWithState.stateInfo.value?.info?.version)
    }

    @Test
    fun `stopUpdateInstall strips v prefix from tag name`() {
        val deviceWithState = makeDeviceWithState("0.13.0")
        val version = makeVersion("v0.14.0-b3")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = true)

        assertEquals("0.14.0-b3", deviceWithState.stateInfo.value?.info?.version)
    }

    @Test
    fun `stopUpdateInstall handles tag without v prefix gracefully`() {
        val deviceWithState = makeDeviceWithState("0.13.0")
        val version = makeVersion("0.14.0")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = true)

        assertEquals("0.14.0", deviceWithState.stateInfo.value?.info?.version)
    }

    @Test
    fun `stopUpdateInstall does not crash when stateInfo is null`() {
        val device = Device(macAddress = "mac2", address = "192.168.0.2")
        val deviceWithState = DeviceWithState(device)
        val version = makeVersion("v0.14.0")

        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = true)

        assertNull(deviceWithState.stateInfo.value)
    }

    @Test
    fun `stopUpdateInstall without arguments does not crash`() {
        viewModel.startUpdateInstall(makeVersion())
        viewModel.stopUpdateInstall()
        // No exception = pass
    }

    // -- Dialog lifecycle methods (smoke tests) --------------------------------

    @Test
    fun `hideUpdateDetails does not crash when called without showUpdateDetails`() {
        viewModel.hideUpdateDetails()
        // No exception = pass
    }

    @Test
    fun `hideUpdateDisclaimer does not crash when called without showUpdateDisclaimer`() {
        viewModel.hideUpdateDisclaimer()
        // No exception = pass
    }

    @Test
    fun `showUpdateDisclaimer then hideUpdateDisclaimer does not crash`() {
        val version = makeVersion()
        viewModel.showUpdateDisclaimer(version)
        viewModel.hideUpdateDisclaimer()
        // No exception = pass
    }

    @Test
    fun `startUpdateInstall then stopUpdateInstall round-trip does not crash`() {
        val version = makeVersion("v0.15.0")
        viewModel.startUpdateInstall(version)
        viewModel.stopUpdateInstall()
        // No exception = pass
    }
}
