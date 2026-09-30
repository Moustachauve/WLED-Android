package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.repository.migrations.UserPreferencesV0ToV1
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UserPreferencesRepositoryTest {

    private fun createRepository(testPath: String): UserPreferencesRepository {
        val dataStore = createUserPreferencesDataStore(
            producePath = { testPath.toPath() },
        )
        return UserPreferencesRepository(dataStore)
    }

    @Test
    fun defaultPreferences_emitExpectedValues() = runTest {
        val testFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "test_user_prefs_${Random.nextLong()}.json"
        try {
            val repo = createRepository(testFile.toString())

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
    fun updateMethods_updateFlowState() = runTest {
        val testFile = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "test_user_prefs_${Random.nextLong()}.json"
        try {
            val repo = createRepository(testFile.toString())

            repo.updateThemeMode(ThemeSettings.Dark)
            assertEquals(ThemeSettings.Dark, repo.themeMode.first())

            repo.updateAutoDiscovery(false)
            assertFalse(repo.autoDiscovery.first())

            repo.updateShowOfflineDeviceLast(false)
            assertFalse(repo.showOfflineDevicesLast.first())

            repo.updateShowHiddenDevices(true)
            assertTrue(repo.showHiddenDevices.first())

            repo.updateLastUpdateCheckDate(123456789L)
            assertEquals(123456789L, repo.lastUpdateCheckDate.first())

            repo.updateLastChangelogVersionSeen("v2.5.0")
            assertEquals("v2.5.0", repo.lastChangelogVersionSeen.first())
        } finally {
            try {
                FileSystem.SYSTEM.delete(testFile)
            } catch (_: Exception) {
            }
        }
    }

    @Test
    fun serializer_roundTripWorksCorrectly() = runTest {
        val serializer = UserPreferencesSerializer()
        val original = UserPreferences(
            selectedDeviceAddress = "192.168.1.50",
            hasMigratedSharedPref = true,
            theme = ThemeSettings.Dark,
            automaticDiscovery = false,
            version = 2,
            showOfflineLast = false,
            sendCrashData = true,
            sendPerformanceData = true,
            lastUpdateCheckDate = 1710000000L,
            dateLastWritten = 1710000010L,
            showHiddenDevices = true,
            lastChangelogVersionSeen = "1.5.0",
        )

        val buffer = Buffer()
        serializer.writeTo(original, buffer)

        val deserialized = serializer.readFrom(buffer)
        assertEquals(original, deserialized)
    }

    @Test
    fun serializer_readEmptyInput_returnsDefaultValue() = runTest {
        val serializer = UserPreferencesSerializer()
        val buffer = Buffer()
        val deserialized = serializer.readFrom(buffer)
        assertEquals(serializer.defaultValue, deserialized)
    }

    @Test
    fun userPreferencesV0ToV1_migratesCorrectly() = runTest {
        val migration = UserPreferencesV0ToV1()

        assertTrue(migration.shouldMigrate(UserPreferences(version = 0)))
        assertTrue(migration.shouldMigrate(UserPreferences(version = -1)))
        assertFalse(migration.shouldMigrate(UserPreferences(version = 1)))

        val oldPrefs = UserPreferences(
            version = 0,
            theme = ThemeSettings.Dark,
            automaticDiscovery = false,
            showOfflineLast = false,
            selectedDeviceAddress = "192.168.1.10",
        )

        val migrated = migration.migrate(oldPrefs)
        assertEquals(1, migrated.version)
        assertEquals(ThemeSettings.Auto, migrated.theme)
        assertTrue(migrated.automaticDiscovery)
        assertTrue(migrated.showOfflineLast)
        assertEquals("192.168.1.10", migrated.selectedDeviceAddress)
    }
}
