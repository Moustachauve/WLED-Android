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
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

/**
 * Unit tests for [DeviceEditViewModel].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceEditViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: DeviceEditViewModel

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
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

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -- Helpers ---------------------------------------------------------------

    private fun makeDeviceWithState(version: String = "16.0.0"): DeviceWithState {
        val device = Device(macAddress = "mac1", address = "192.168.0.1")
        return DeviceWithState(
            device = device,
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
    }

    private fun makeVersion(tagName: String = "16.0.1"): VersionWithAssets = VersionWithAssets(
        version = Version.getPreviewVersion().copy(tagName = tagName),
        assets = emptyList(),
    )

    // -- stopUpdateInstall: version patching -----------------------------------

    @ParameterizedTest
    @CsvSource(
        "v16.0.1, true, 16.0.1",
        "v16.0.1, false, 16.0.0",
        "v16.0.1-b1, true, 16.0.1-b1",
        "16.0.1, true, 16.0.1",
    )
    fun `stopUpdateInstall updates stateInfo version according to success and tag formatting`(
        tagName: String,
        wasSuccessful: Boolean,
        expectedVersion: String,
    ) {
        val deviceWithState = makeDeviceWithState("16.0.0")
        val version = makeVersion(tagName)

        viewModel.startUpdateInstall(version)
        val result = viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = wasSuccessful)

        if (wasSuccessful) {
            assertEquals(expectedVersion, result?.stateInfo?.info?.version)
            assertNull(result?.updateVersionTag)
        } else {
            assertNull(result)
        }
    }

    @Test
    fun `stopUpdateInstall returns null when stateInfo is null`() {
        val device = Device(macAddress = "mac2", address = "192.168.0.2")
        val deviceWithState = DeviceWithState(device)
        val version = makeVersion("v16.0.1")

        viewModel.startUpdateInstall(version)
        val result = viewModel.stopUpdateInstall(deviceWithState, version, wasSuccessful = true)

        assertNull(result)
    }

    // -- Dialog lifecycle methods ---------------------------------------------

    @Test
    fun `startUpdateInstall sets install version and stopUpdateInstall clears it`() = runTest(testDispatcher) {
        val job = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        val version = makeVersion("16.0.1")

        viewModel.startUpdateInstall(version)
        advanceUntilIdle()
        assertEquals(version, viewModel.uiState.value.updateInstallVersion)

        viewModel.stopUpdateInstall()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.updateInstallVersion)

        job.cancel()
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
