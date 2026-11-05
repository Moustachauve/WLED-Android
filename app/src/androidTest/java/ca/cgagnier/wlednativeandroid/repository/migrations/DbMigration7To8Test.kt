package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbMigration7To8Test {

    private val testDb = "migration-test-7-8"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = context.getDatabasePath(testDb)

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = dbFile,
        driver = BundledSQLiteDriver(),
        databaseClass = DevicesDatabase::class,
    )

    @Before
    fun setUp() {
        context.deleteDatabase(testDb)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(testDb)
    }

    @Test
    fun migrate7To8_migratesDevicesAndFiltersUnknownMacs() {
        helper.createDatabase(7).use { db ->
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
        }

        helper.runMigrationsAndValidate(8).use { migratedDb ->
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
        }
    }
}
