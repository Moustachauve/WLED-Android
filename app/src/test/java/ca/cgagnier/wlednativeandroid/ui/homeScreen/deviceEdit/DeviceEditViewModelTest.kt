package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.content.Context
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
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

    private fun makeVersion(tagName: String = "16.0.1"): VersionWithAssets = VersionWithAssets(
        version = Version.getPreviewVersion().copy(tagName = tagName),
        assets = emptyList(),
    )

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
