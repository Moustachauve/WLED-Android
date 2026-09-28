package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DbMigration7To8Test {

    private val testDb = "migration-test-7-8"
    private val dbFile = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(testDb).absolutePath,
    )

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = DevicesDatabase::class,
    )

    @Before
    fun setUp() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun migrate7To8_migratesDevicesAndFiltersUnknownMacs() {
        val db = helper.createDatabase(7)

        // Insert device with custom name
        db.execSQL(
            """
            INSERT INTO Device (
                address, name, isCustomName, isHidden, macAddress, brightness, color,
                isPoweredOn, isOnline, isRefreshing, networkBssid, networkRssi, networkSignal,
                networkChannel, isEthernet, platformName, version, newUpdateVersionTagAvailable,
                skipUpdateTag, branch, brand, productName, release, batteryPercentage, hasBattery
            )
            VALUES (
                '192.168.1.100', 'Living Room', 1, 0, '11:22:33:44:55:66', 128, 0,
                1, 1, 0, '__unknown__', 0, 0,
                0, 0, '__unknown__', '__unknown__', '',
                '', 'UNKNOWN', '__unknown__', '__unknown__', '__unknown__', 0.0, 0
            )
            """.trimIndent(),
        )

        // Insert device with default/original name
        db.execSQL(
            """
            INSERT INTO Device (
                address, name, isCustomName, isHidden, macAddress, brightness, color,
                isPoweredOn, isOnline, isRefreshing, networkBssid, networkRssi, networkSignal,
                networkChannel, isEthernet, platformName, version, newUpdateVersionTagAvailable,
                skipUpdateTag, branch, brand, productName, release, batteryPercentage, hasBattery
            )
            VALUES (
                '192.168.1.101', 'WLED-Bedroom', 0, 1, 'AA:BB:CC:DD:EE:FF', 255, 0,
                0, 0, 0, '__unknown__', 0, 0,
                0, 0, '__unknown__', '__unknown__', '',
                '', 'UNKNOWN', '__unknown__', '__unknown__', '__unknown__', 0.0, 0
            )
            """.trimIndent(),
        )

        // Insert device with unknown MAC address (should NOT be migrated)
        db.execSQL(
            """
            INSERT INTO Device (
                address, name, isCustomName, isHidden, macAddress, brightness, color,
                isPoweredOn, isOnline, isRefreshing, networkBssid, networkRssi, networkSignal,
                networkChannel, isEthernet, platformName, version, newUpdateVersionTagAvailable,
                skipUpdateTag, branch, brand, productName, release, batteryPercentage, hasBattery
            )
            VALUES (
                '192.168.1.102', 'WLED-Unknown', 0, 0, '__unknown__', 0, 0,
                0, 0, 0, '__unknown__', 0, 0,
                0, 0, '__unknown__', '__unknown__', '',
                '', 'UNKNOWN', '__unknown__', '__unknown__', '__unknown__', 0.0, 0
            )
            """.trimIndent(),
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(8)

        // Validate Device2 has exactly 2 migrated devices
        migratedDb.prepare("SELECT COUNT(*) FROM Device2").use { stmt ->
            assertTrue(stmt.step())
            assertEquals(2, stmt.getInt(0))
        }

        // Validate Device 1 (custom name)
        migratedDb.prepare("SELECT * FROM Device2 WHERE macAddress = '11:22:33:44:55:66'").use { stmt ->
            assertTrue(stmt.step())
            val colNames = stmt.getColumnNames()
            assertEquals("192.168.1.100", stmt.getText(colNames.indexOf("address")))
            assertEquals("Living Room", stmt.getText(colNames.indexOf("customName")))
            assertEquals("", stmt.getText(colNames.indexOf("originalName")))
            assertEquals(0, stmt.getInt(colNames.indexOf("isHidden")))
            assertEquals("UNKNOWN", stmt.getText(colNames.indexOf("branch")))
        }

        // Validate Device 2 (original name)
        migratedDb.prepare("SELECT * FROM Device2 WHERE macAddress = 'AA:BB:CC:DD:EE:FF'").use { stmt ->
            assertTrue(stmt.step())
            val colNames = stmt.getColumnNames()
            assertEquals("192.168.1.101", stmt.getText(colNames.indexOf("address")))
            assertEquals("", stmt.getText(colNames.indexOf("customName")))
            assertEquals("WLED-Bedroom", stmt.getText(colNames.indexOf("originalName")))
            assertEquals(1, stmt.getInt(colNames.indexOf("isHidden")))
            assertEquals("UNKNOWN", stmt.getText(colNames.indexOf("branch")))
        }

        // Validate unknown MAC is not migrated
        migratedDb.prepare("SELECT * FROM Device2 WHERE macAddress = '__unknown__'").use { stmt ->
            assertFalse(stmt.step())
        }

        migratedDb.close()
    }

    @Test
    fun migrate7To10_fullMigrationPath() {
        val db = helper.createDatabase(7)

        db.execSQL(
            """
            INSERT INTO Device (
                address, name, isCustomName, isHidden, macAddress, brightness, color,
                isPoweredOn, isOnline, isRefreshing, networkBssid, networkRssi, networkSignal,
                networkChannel, isEthernet, platformName, version, newUpdateVersionTagAvailable,
                skipUpdateTag, branch, brand, productName, release, batteryPercentage, hasBattery
            )
            VALUES (
                '192.168.1.100', 'Living Room', 1, 0, '11:22:33:44:55:66', 128, 0,
                1, 1, 0, '__unknown__', 0, 0,
                0, 0, '__unknown__', '__unknown__', '',
                '', 'UNKNOWN', '__unknown__', '__unknown__', '__unknown__', 0.0, 0
            )
            """.trimIndent(),
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10))

        // Validate Device2 is present and has repositoryId 1
        migratedDb.prepare("SELECT * FROM Device2 WHERE macAddress = '11:22:33:44:55:66'").use { stmt ->
            assertTrue(stmt.step())
            val colNames = stmt.getColumnNames()
            assertEquals("Living Room", stmt.getText(colNames.indexOf("customName")))
            assertEquals(1, stmt.getInt(colNames.indexOf("repositoryId")))
        }

        // Validate old Device table was dropped in migration 8->9
        migratedDb.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='Device'").use { stmt ->
            assertFalse(stmt.step())
        }

        // Validate Repository table exists with default WLED repository
        migratedDb.prepare("SELECT * FROM Repository WHERE id = 1").use { stmt ->
            assertTrue(stmt.step())
            val colNames = stmt.getColumnNames()
            assertEquals("WLED", stmt.getText(colNames.indexOf("name")))
            assertEquals("wled/WLED", stmt.getText(colNames.indexOf("ownerAndRepo")))
        }

        migratedDb.close()
    }

    @Test
    fun migrate1To10_allMigrationsPass() {
        val db = helper.createDatabase(1)

        db.execSQL(
            """
            INSERT INTO Device (address, name, isCustomName, isHidden, brightness, color, isPoweredOn, isOnline, isRefreshing, networkRssi)
            VALUES ('192.168.1.50', 'Original Device', 0, 0, 128, 0, 1, 1, 0, -65)
            """.trimIndent(),
        )

        db.close()

        val migratedDb = helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10))

        // Validate Repository table exists with default WLED repository
        migratedDb.prepare("SELECT * FROM Repository WHERE id = 1").use { stmt ->
            assertTrue(stmt.step())
            val colNames = stmt.getColumnNames()
            assertEquals("WLED", stmt.getText(colNames.indexOf("name")))
            assertEquals("wled/WLED", stmt.getText(colNames.indexOf("ownerAndRepo")))
        }

        // Validate old Device table was dropped in migration 8->9
        migratedDb.prepare("SELECT name FROM sqlite_master WHERE type='table' AND name='Device'").use { stmt ->
            assertFalse(stmt.step())
        }

        migratedDb.close()
    }

    @Test
    fun migrateEachStepFrom1To7() {
        for (version in 1..6) {
            val stepDb = "migration-step-$version"
            val stepDbFile = File(
                InstrumentationRegistry.getInstrumentation().targetContext.getDatabasePath(stepDb).absolutePath,
            )
            if (stepDbFile.exists()) {
                stepDbFile.delete()
            }
            val stepHelper = MigrationTestHelper(
                instrumentation = InstrumentationRegistry.getInstrumentation(),
                file = stepDbFile,
                driver = BundledSQLiteDriver(),
                databaseClass = DevicesDatabase::class,
            )
            val db = stepHelper.createDatabase(version)
            db.close()
            val migrated = stepHelper.runMigrationsAndValidate(version + 1)
            migrated.close()
            stepDbFile.delete()
        }
    }
}
