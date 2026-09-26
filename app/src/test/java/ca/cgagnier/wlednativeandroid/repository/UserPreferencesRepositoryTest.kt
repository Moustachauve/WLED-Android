package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UserPreferencesRepositoryTest {

    @TempDir
    lateinit var tempFolder: File

    private fun createRepository(): UserPreferencesRepository {
        val testFile = File(tempFolder, "test_user_preferences.json")
        val dataStore = DataStoreFactory.create(
            serializer = UserPreferencesSerializer(),
            produceFile = { testFile },
        )
        return UserPreferencesRepository(dataStore)
    }

    @Test
    fun defaultPreferences_emitExpectedValues() = runBlocking {
        val repo = createRepository()

        assertEquals(ThemeSettings.Auto, repo.themeMode.first())
        assertTrue(repo.autoDiscovery.first())
        assertTrue(repo.showOfflineDevicesLast.first())
        assertFalse(repo.showHiddenDevices.first())
        assertEquals(0L, repo.lastUpdateCheckDate.first())
        assertEquals("", repo.lastChangelogVersionSeen.first())
    }

    @Test
    fun updateThemeMode_updatesFlow() = runBlocking {
        val repo = createRepository()

        repo.updateThemeMode(ThemeSettings.Dark)
        assertEquals(ThemeSettings.Dark, repo.themeMode.first())

        repo.updateThemeMode(ThemeSettings.Light)
        assertEquals(ThemeSettings.Light, repo.themeMode.first())
    }

    @Test
    fun updateAutoDiscovery_updatesFlow() = runBlocking {
        val repo = createRepository()

        repo.updateAutoDiscovery(false)
        assertFalse(repo.autoDiscovery.first())

        repo.updateAutoDiscovery(true)
        assertTrue(repo.autoDiscovery.first())
    }

    @Test
    fun updateShowOfflineDeviceLast_updatesFlow() = runBlocking {
        val repo = createRepository()

        repo.updateShowOfflineDeviceLast(false)
        assertFalse(repo.showOfflineDevicesLast.first())

        repo.updateShowOfflineDeviceLast(true)
        assertTrue(repo.showOfflineDevicesLast.first())
    }

    @Test
    fun updateShowHiddenDevices_updatesFlow() = runBlocking {
        val repo = createRepository()

        repo.updateShowHiddenDevices(true)
        assertTrue(repo.showHiddenDevices.first())

        repo.updateShowHiddenDevices(false)
        assertFalse(repo.showHiddenDevices.first())
    }

    @Test
    fun updateLastUpdateCheckDate_updatesFlow() = runBlocking {
        val repo = createRepository()

        repo.updateLastUpdateCheckDate(123456789L)
        assertEquals(123456789L, repo.lastUpdateCheckDate.first())
    }

    @Test
    fun updateLastChangelogVersionSeen_updatesFlow() = runBlocking {
        val repo = createRepository()

        repo.updateLastChangelogVersionSeen("v2.5.0")
        assertEquals("v2.5.0", repo.lastChangelogVersionSeen.first())
    }
}
