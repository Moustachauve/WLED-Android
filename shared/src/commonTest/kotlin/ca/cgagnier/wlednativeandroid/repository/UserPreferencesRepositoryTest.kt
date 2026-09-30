package ca.cgagnier.wlednativeandroid.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
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

    private fun newTestPath(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "test_user_prefs_${Random.nextLong()}.json"

    @Test
    fun defaultPreferences_emitExpectedValues() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            assertEquals(ThemeSettings.Auto, repo.themeMode.first())
            assertTrue(repo.autoDiscovery.first())
            assertTrue(repo.showOfflineDevicesLast.first())
            assertFalse(repo.showHiddenDevices.first())
            assertEquals(0L, repo.lastUpdateCheckDate.first())
            assertEquals("", repo.lastChangelogVersionSeen.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun updateThemeMode_updatesFlow() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            repo.updateThemeMode(ThemeSettings.Dark)
            assertEquals(ThemeSettings.Dark, repo.themeMode.first())

            repo.updateThemeMode(ThemeSettings.Light)
            assertEquals(ThemeSettings.Light, repo.themeMode.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun updateAutoDiscovery_updatesFlow() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            repo.updateAutoDiscovery(false)
            assertFalse(repo.autoDiscovery.first())

            repo.updateAutoDiscovery(true)
            assertTrue(repo.autoDiscovery.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun updateShowOfflineDeviceLast_updatesFlow() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            repo.updateShowOfflineDeviceLast(false)
            assertFalse(repo.showOfflineDevicesLast.first())

            repo.updateShowOfflineDeviceLast(true)
            assertTrue(repo.showOfflineDevicesLast.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun updateShowHiddenDevices_updatesFlow() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            repo.updateShowHiddenDevices(true)
            assertTrue(repo.showHiddenDevices.first())

            repo.updateShowHiddenDevices(false)
            assertFalse(repo.showHiddenDevices.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun updateLastUpdateCheckDate_updatesFlow() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            repo.updateLastUpdateCheckDate(123456789L)
            assertEquals(123456789L, repo.lastUpdateCheckDate.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun updateLastChangelogVersionSeen_updatesFlow() = runTest {
        val testFile = newTestPath()
        try {
            val repo = createRepository(testFile, backgroundScope)

            repo.updateLastChangelogVersionSeen("v2.5.0")
            assertEquals("v2.5.0", repo.lastChangelogVersionSeen.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }
}
