package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class UserPreferencesRepositoryTest {

    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var dataStore: DataStore<UserPreferences>

    @Before
    fun setUp() {
        dataStore = mock()
        userPreferencesRepository = UserPreferencesRepository(dataStore)
    }

    @Test
    fun themeMode_returnsCorrectValue() = runTest {
        val expectedTheme = ThemeSettings.Light
        val preferences = UserPreferences.newBuilder().setTheme(expectedTheme).build()
        whenever(dataStore.data).thenReturn(flowOf(preferences))

        val theme = userPreferencesRepository.themeMode.first()

        assertEquals(expectedTheme, theme)
    }

    @Test
    fun autoDiscovery_returnsCorrectValue() = runTest {
        val expectedAutoDiscovery = true
        val preferences = UserPreferences.newBuilder().setAutomaticDiscovery(expectedAutoDiscovery).build()
        whenever(dataStore.data).thenReturn(flowOf(preferences))

        val autoDiscovery = userPreferencesRepository.autoDiscovery.first()

        assertEquals(expectedAutoDiscovery, autoDiscovery)
    }
}
