package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DbMigration9To10Test {

    private val testDb = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DevicesDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate9To10() {
        var db = helper.createDatabase(testDb, 9)

        // Insert Device2
        db.execSQL(
            """
            INSERT INTO Device2 (macAddress, address, isHidden, originalName, customName, skipUpdateTag, branch, lastSeen)
            VALUES ('11:22:33:44:55:66', '192.168.1.100', 0, 'Original Name', 'Custom Name', '', 'UNKNOWN', 0)
            """.trimIndent(),
        )

        // Insert Version
        db.execSQL(
            """
            INSERT INTO Version (tagName, name, description, isPrerelease, publishedDate, htmlUrl)
            VALUES ('v1.0.0', 'Release 1', 'Test Description', 0, '2023-01-01', 'http://example.com')
            """.trimIndent(),
        )

        // Insert Asset
        db.execSQL(
            """
            INSERT INTO Asset (versionTagName, name, size, downloadUrl, assetId)
            VALUES ('v1.0.0', 'asset.bin', 1048576, 'http://example.com/asset.bin', 123)
            """.trimIndent(),
        )

        // Close db before migration run
        db.close()

        // Run migration
        db = helper.runMigrationsAndValidate(testDb, 10, true, MIGRATION_9_10)

        // Validate Device2
        var cursor = db.query("SELECT * FROM Device2 WHERE macAddress = '11:22:33:44:55:66'")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("repositoryId")))
        cursor.close()

        // Validate Repository 1 (WLED)
        cursor = db.query("SELECT * FROM Repository WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals("WLED", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals("wled/WLED", cursor.getString(cursor.getColumnIndexOrThrow("ownerAndRepo")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isDefault")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isEnabled")))
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isUpdateEnabled")))
        cursor.close()

        // Validate Version
        cursor = db.query("SELECT * FROM Version WHERE tagName = 'v1.0.0'")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("repositoryId")))
        val versionId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
        cursor.close()

        // Validate Asset
        cursor = db.query("SELECT * FROM Asset WHERE name = 'asset.bin'")
        assertTrue(cursor.moveToFirst())
        assertEquals(versionId, cursor.getInt(cursor.getColumnIndexOrThrow("versionId")))
        cursor.close()
    }
}
