package ca.cgagnier.wlednativeandroid.repository.migrations

import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import com.diffplug.selfie.Selfie.expectSelfie
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import ca.cgagnier.wlednativeandroid.repository.legacy.ThemeSettings as LegacyProtoThemeSettings
import ca.cgagnier.wlednativeandroid.repository.legacy.UserPreferences as LegacyProtoUserPreferences

private val prettyJson = Json {
    prettyPrint = true
    prettyPrintIndent = "  "
    encodeDefaults = true
}

class LegacyProtoToKotlinxPreferencesMigrationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun shouldMigrate_whenFileDoesNotExist_returnsFalse() = runBlocking {
        val nonExistentFile = File(tempFolder.root, "non_existent.pb")
        val migration = LegacyProtoToKotlinxPreferencesMigration(nonExistentFile)

        assertFalse(migration.shouldMigrate(UserPreferences()))
    }

    @Test
    fun shouldMigrate_whenFileIsEmpty_returnsFalse() = runBlocking {
        val emptyFile = tempFolder.newFile("empty.pb")
        val migration = LegacyProtoToKotlinxPreferencesMigration(emptyFile)

        assertFalse(migration.shouldMigrate(UserPreferences()))
    }

    @Test
    fun shouldMigrate_whenFileHasContent_returnsTrue() = runBlocking {
        val protoFile = tempFolder.newFile("user_prefs.pb")
        protoFile.writeBytes(byteArrayOf(1, 2, 3))
        val migration = LegacyProtoToKotlinxPreferencesMigration(protoFile)

        assertTrue(migration.shouldMigrate(UserPreferences()))
    }

    @Test
    fun migrate_withLegacyProto_migratesAllFieldsAccurately() {
        runBlocking {
            val protoFile = tempFolder.newFile("user_prefs.pb")
            val legacyProto = LegacyProtoUserPreferences.newBuilder()
                .setSelectedDeviceAddress("10.0.0.42")
                .setHasMigratedSharedPref(true)
                .setTheme(LegacyProtoThemeSettings.Dark)
                .setAutomaticDiscovery(false)
                .setVersion(3)
                .setShowOfflineLast(false)
                .setSendCrashData(true)
                .setSendPerformanceData(true)
                .setLastUpdateCheckDate(1705000000L)
                .setDateLastWritten(1705000100L)
                .setShowHiddenDevices(true)
                .setLastChangelogVersionSeen("2.1.0")
                .build()

            FileOutputStream(protoFile).use { legacyProto.writeTo(it) }

            val migration = LegacyProtoToKotlinxPreferencesMigration(protoFile)
            val initialPreferences = UserPreferences()
            val migrated = migration.migrate(initialPreferences)

            expectSelfie(prettyJson.encodeToString(migrated)).toMatchDisk()
        }
    }

    @Test
    fun migrate_withLightAndAutoThemes_mapsCorrectly() = runBlocking {
        val protoFileLight = tempFolder.newFile("user_prefs_light.pb")
        val legacyLight = LegacyProtoUserPreferences.newBuilder()
            .setTheme(LegacyProtoThemeSettings.Light)
            .setVersion(1)
            .build()
        FileOutputStream(protoFileLight).use { legacyLight.writeTo(it) }
        val migratedLight = LegacyProtoToKotlinxPreferencesMigration(protoFileLight).migrate(UserPreferences())
        assertEquals(ThemeSettings.Light, migratedLight.theme)

        val protoFileAuto = tempFolder.newFile("user_prefs_auto.pb")
        val legacyAuto = LegacyProtoUserPreferences.newBuilder()
            .setTheme(LegacyProtoThemeSettings.Auto)
            .setVersion(1)
            .build()
        FileOutputStream(protoFileAuto).use { legacyAuto.writeTo(it) }
        val migratedAuto = LegacyProtoToKotlinxPreferencesMigration(protoFileAuto).migrate(UserPreferences())
        assertEquals(ThemeSettings.Auto, migratedAuto.theme)
    }

    @Test
    fun migrate_withV0LegacyProto_setsExpectedDefaultsAndResetsDataSharingFlags() {
        runBlocking {
            val protoFile = tempFolder.newFile("user_prefs_v0.pb")
            val legacyProto = LegacyProtoUserPreferences.newBuilder()
                .setVersion(0)
                .setSelectedDeviceAddress("192.168.1.99")
                .setSendCrashData(true)
                .setSendPerformanceData(true)
                .build()

            FileOutputStream(protoFile).use { legacyProto.writeTo(it) }
            assertTrue(protoFile.length() > 0)

            val migration = LegacyProtoToKotlinxPreferencesMigration(protoFile)
            val migrated = migration.migrate(UserPreferences())

            expectSelfie(prettyJson.encodeToString(migrated)).toMatchDisk()
        }
    }

    @Test
    fun migrate_withCorruptFile_throwsExceptionAndRetainsFile() {
        val protoFile = tempFolder.newFile("corrupt.pb")
        protoFile.writeText("corrupt binary data not a protobuf")

        val migration = LegacyProtoToKotlinxPreferencesMigration(protoFile)
        val defaultPrefs = UserPreferences(version = 1)

        assertThrows(Exception::class.java) {
            runBlocking {
                migration.migrate(defaultPrefs)
            }
        }
        assertTrue(protoFile.exists())
    }

    @Test
    fun cleanUp_deletesLegacyFile() = runBlocking {
        val protoFile = tempFolder.newFile("user_prefs_cleanup.pb")
        protoFile.writeBytes(byteArrayOf(1, 2, 3))
        assertTrue(protoFile.exists())

        val migration = LegacyProtoToKotlinxPreferencesMigration(protoFile)
        migration.cleanUp()

        assertFalse(protoFile.exists())
        assertFalse(migration.shouldMigrate(UserPreferences()))
    }

    @Test
    fun cleanUp_whenDeleteFails_throwsIOException() {
        val mockFile = mockk<File>()
        every { mockFile.exists() } returns true
        every { mockFile.delete() } returns false
        every { mockFile.absolutePath } returns "/fake/path/user_prefs.pb"

        val migration = LegacyProtoToKotlinxPreferencesMigration(mockFile)

        assertThrows(IOException::class.java) {
            runBlocking {
                migration.cleanUp()
            }
        }
    }
}
