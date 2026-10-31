package ca.cgagnier.wlednativeandroid.ui.theme

import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val preferencesRepository: UserPreferencesRepository = mockk()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme is initially null before preferences emit`() = runTest(testDispatcher) {
        every { preferencesRepository.themeMode } returns MutableSharedFlow()

        val viewModel = ThemeViewModel(preferencesRepository)

        assertNull(viewModel.theme.value)
    }

    @Test
    fun `theme reflects stored preference once loaded`() = runTest(testDispatcher) {
        val themeFlow = MutableStateFlow(ThemeSettings.Dark)
        every { preferencesRepository.themeMode } returns themeFlow

        val viewModel = ThemeViewModel(preferencesRepository)
        advanceUntilIdle()

        assertEquals(ThemeSettings.Dark, viewModel.theme.value)
    }
}
