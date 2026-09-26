package ca.cgagnier.wlednativeandroid.repository.migrations

import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import com.diffplug.selfie.Selfie.expectSelfie
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
}

class UserPreferencesV0ToV1Test {

    private val migration = UserPreferencesV0ToV1()

    @Test
    fun shouldMigrate_whenVersionZeroOrNegative_returnsTrue() = runBlocking {
        assertTrue(migration.shouldMigrate(UserPreferences(version = 0)))
        assertTrue(migration.shouldMigrate(UserPreferences(version = -1)))
    }

    @Test
    fun shouldMigrate_whenVersionPositive_returnsFalse() = runBlocking {
        assertFalse(migration.shouldMigrate(UserPreferences(version = 1)))
        assertFalse(migration.shouldMigrate(UserPreferences(version = 2)))
    }

    @Test
    fun migrate_setsExpectedDefaultsAndVersion1() {
        runBlocking {
            val oldPrefs = UserPreferences(
                version = 0,
                theme = ThemeSettings.Dark,
                automaticDiscovery = false,
                showOfflineLast = false,
                sendCrashData = true,
                sendPerformanceData = true,
                selectedDeviceAddress = "192.168.1.10",
            )

            val migrated = migration.migrate(oldPrefs)

            expectSelfie(prettyJson.encodeToString(migrated)).toMatchDisk()
        }
    }
}
