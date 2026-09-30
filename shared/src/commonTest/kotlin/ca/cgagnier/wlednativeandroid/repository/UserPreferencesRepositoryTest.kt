package ca.cgagnier.wlednativeandroid.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserPreferencesRepositoryTest {

    private fun createRepository(testPath: Path, scope: CoroutineScope): UserPreferencesRepository {
        val dataStore = createUserPreferencesDataStore(
            producePath = { testPath },
            scope = scope,
        )
        return UserPreferencesRepository(dataStore)
    }

    private fun newTestDir(): Path = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "datastore_test_${Random.nextLong()}"

    private fun withTestDir(block: suspend TestScope.(testFile: Path) -> Unit) = runTest {
        val testDir = newTestDir()
        FileSystem.SYSTEM.createDirectories(testDir)
        val testFile = testDir / USER_PREFERENCES_DATA_STORE_FILE_NAME
        try {
            block(testFile)
        } finally {
            try {
                FileSystem.SYSTEM.deleteRecursively(testDir)
            } catch (_: Exception) {
            }
        }
    }

    private fun withRepository(block: suspend TestScope.(repo: UserPreferencesRepository) -> Unit) =
        withTestDir { testFile ->
            val repo = createRepository(testFile, backgroundScope)
            block(repo)
        }

    @Test
    fun defaultPreferences_emitExpectedValues() = withRepository { repo ->
        assertEquals(ThemeSettings.Auto, repo.themeMode.first())
        assertTrue(repo.autoDiscovery.first())
        assertTrue(repo.showOfflineDevicesLast.first())
        assertFalse(repo.showHiddenDevices.first())
        assertEquals(0L, repo.lastUpdateCheckDate.first())
        assertEquals("", repo.lastChangelogVersionSeen.first())
    }

    @Test
    fun updateThemeMode_updatesFlow() = withRepository { repo ->
        repo.updateThemeMode(ThemeSettings.Dark)
        assertEquals(ThemeSettings.Dark, repo.themeMode.first())

        repo.updateThemeMode(ThemeSettings.Light)
        assertEquals(ThemeSettings.Light, repo.themeMode.first())
    }

    @Test
    fun updateAutoDiscovery_updatesFlow() = withRepository { repo ->
        repo.updateAutoDiscovery(false)
        assertFalse(repo.autoDiscovery.first())

        repo.updateAutoDiscovery(true)
        assertTrue(repo.autoDiscovery.first())
    }

    @Test
    fun updateShowOfflineDeviceLast_updatesFlow() = withRepository { repo ->
        repo.updateShowOfflineDeviceLast(false)
        assertFalse(repo.showOfflineDevicesLast.first())

        repo.updateShowOfflineDeviceLast(true)
        assertTrue(repo.showOfflineDevicesLast.first())
    }

    @Test
    fun updateShowHiddenDevices_updatesFlow() = withRepository { repo ->
        repo.updateShowHiddenDevices(true)
        assertTrue(repo.showHiddenDevices.first())

        repo.updateShowHiddenDevices(false)
        assertFalse(repo.showHiddenDevices.first())
    }

    @Test
    fun updateLastUpdateCheckDate_updatesFlow() = withRepository { repo ->
        repo.updateLastUpdateCheckDate(123456789L)
        assertEquals(123456789L, repo.lastUpdateCheckDate.first())
    }

    @Test
    fun updateLastChangelogVersionSeen_updatesFlow() = withRepository { repo ->
        repo.updateLastChangelogVersionSeen("v2.5.0")
        assertEquals("v2.5.0", repo.lastChangelogVersionSeen.first())
    }

    @Test
    fun updates_persistAcrossRepositoryRecreation() = withTestDir { testFile ->
        val scope1 = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        try {
            val repo1 = createRepository(testFile, scope1)
            repo1.updateThemeMode(ThemeSettings.Dark)
            repo1.updateAutoDiscovery(false)
            repo1.updateShowOfflineDeviceLast(false)
            repo1.updateShowHiddenDevices(true)
            repo1.updateLastUpdateCheckDate(987654321L)
            repo1.updateLastChangelogVersionSeen("v3.0.0")
        } finally {
            // Cancel scope1 to simulate process termination
            scope1.cancel()
        }

        // Create a new instance pointing to the exact same file path
        val repo2 = createRepository(testFile, backgroundScope)
        assertEquals(ThemeSettings.Dark, repo2.themeMode.first())
        assertFalse(repo2.autoDiscovery.first())
        assertFalse(repo2.showOfflineDevicesLast.first())
        assertTrue(repo2.showHiddenDevices.first())
        assertEquals(987654321L, repo2.lastUpdateCheckDate.first())
        assertEquals("v3.0.0", repo2.lastChangelogVersionSeen.first())
    }
}
