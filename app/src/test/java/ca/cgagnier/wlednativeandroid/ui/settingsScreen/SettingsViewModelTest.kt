package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncManager
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesRepository: UserPreferencesRepository = mockk(relaxed = true)
    private val soundSyncManager: SoundSyncManager = mockk(relaxed = true)
    private val soundSyncBetaEnabledFlow = MutableStateFlow(false)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { preferencesRepository.themeMode } returns flowOf(ThemeSettings.Auto)
        every { preferencesRepository.autoDiscovery } returns flowOf(true)
        every { preferencesRepository.showOfflineDevicesLast } returns flowOf(true)
        every { preferencesRepository.showHiddenDevices } returns flowOf(false)
        every { preferencesRepository.soundSyncBetaEnabled } returns soundSyncBetaEnabledFlow
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel = SettingsViewModel(
        preferencesRepository = preferencesRepository,
        soundSyncManager = soundSyncManager,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `settingsState reflects soundSyncBetaEnabled flow`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.settingsState.collect()
        }

        assertFalse(viewModel.settingsState.value.isSoundSyncBetaEnabled)

        soundSyncBetaEnabledFlow.value = true
        advanceUntilIdle()

        assertTrue(viewModel.settingsState.value.isSoundSyncBetaEnabled)
    }

    @Test
    fun `setSoundSyncBetaEnabled true updates preference without stopping sound sync`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.setSoundSyncBetaEnabled(true)
        advanceUntilIdle()

        verify(exactly = 0) { soundSyncManager.stop() }
        coVerify { preferencesRepository.updateSoundSyncBetaEnabled(true) }
    }

    @Test
    fun `setSoundSyncBetaEnabled false stops sound sync and updates preference`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.setSoundSyncBetaEnabled(false)
        advanceUntilIdle()

        verify(exactly = 1) { soundSyncManager.stop() }
        coVerify { preferencesRepository.updateSoundSyncBetaEnabled(false) }
    }
}
