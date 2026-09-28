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
import java.io.File

@RunWith(AndroidJUnit4::class)
class DevicesDatabaseHistoricalMigrationsTest {

    private val testDb = "historical-migration-test"
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = File(context.getDatabasePath(testDb).absolutePath)

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

        helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10)).use { migratedDb ->
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
        }
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

        helper.runMigrationsAndValidate(10, listOf(MIGRATION_9_10)).use { migratedDb ->
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

            // Schema 1->2 auto-migration assigned defaultValue '' to macAddress,
            // which is preserved into Device2 by DbMigration7To8
            migratedDb.prepare("SELECT macAddress, address, originalName FROM Device2").use { stmt ->
                assertTrue(stmt.step())
                val colNames = stmt.getColumnNames()
                assertEquals("", stmt.getText(colNames.indexOf("macAddress")))
                assertEquals("192.168.1.50", stmt.getText(colNames.indexOf("address")))
                assertEquals("Original Device", stmt.getText(colNames.indexOf("originalName")))
            }
        }
    }

    @Test
    fun migrateEachStepFrom1To7() {
        for (version in 1..6) {
            val stepDb = "migration-step-$version"
            val stepDbFile = File(context.getDatabasePath(stepDb).absolutePath)
            context.deleteDatabase(stepDb)

            try {
                val stepHelper = MigrationTestHelper(
                    instrumentation = InstrumentationRegistry.getInstrumentation(),
                    file = stepDbFile,
                    driver = BundledSQLiteDriver(),
                    databaseClass = DevicesDatabase::class,
                )
                stepHelper.createDatabase(version).close()
                stepHelper.runMigrationsAndValidate(version + 1).close()
            } finally {
                context.deleteDatabase(stepDb)
            }
        }
    }
}
