package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "DbMigration9To10"
private const val FROM_VERSION = 9
private const val TO_VERSION = 10

/**
 * Migration from 9->10 adds repository information to Version and Asset tables
 * to support tracking releases from multiple WLED repositories/forks.
 *
 * We rename the old tables, create new ones with repository field,
 * copy existing data with default repository "wled/WLED", then drop the old tables.
 */
val MIGRATION_9_10 = object : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Starting migration from 9 to 10")

        renameOldTables(db)
        createNewTables(db)
        migrateVersionData(db)
        migrateAssetData(db)
        dropOldTables(db)
        createIndices(db)

        Log.i(TAG, "Migration from 9 to 10 complete!")
    }

    private fun renameOldTables(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Version` RENAME TO `Version_old`")
        db.execSQL("ALTER TABLE `Asset` RENAME TO `Asset_old`")
    }

    private fun createNewTables(db: SupportSQLiteDatabase) {
        // Create new Version table with repository column
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Version` (
                `tagName` TEXT NOT NULL,
                `repository` TEXT NOT NULL DEFAULT 'wled/WLED',
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `isPrerelease` INTEGER NOT NULL,
                `publishedDate` TEXT NOT NULL,
                `htmlUrl` TEXT NOT NULL,
                PRIMARY KEY(`tagName`, `repository`)
            )
            """.trimIndent(),
        )

        // Create new Asset table with repository column
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Asset` (
                `versionTagName` TEXT NOT NULL,
                `repository` TEXT NOT NULL DEFAULT 'wled/WLED',
                `name` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `downloadUrl` TEXT NOT NULL,
                `assetId` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`versionTagName`, `repository`, `name`),
                FOREIGN KEY(`versionTagName`, `repository`)
                    REFERENCES `Version`(`tagName`, `repository`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun migrateVersionData(db: SupportSQLiteDatabase) {
        val originalCount = getRowCount(db, "Version_old")
        Log.i(TAG, "Total versions in old 'Version' table: $originalCount")

        // Copy data from Version_old to Version with default repository
        db.execSQL(
            """
            INSERT OR IGNORE INTO Version (
                tagName,
                repository,
                name,
                description,
                isPrerelease,
                publishedDate,
                htmlUrl
            )
            SELECT
                tagName,
                'wled/WLED' AS repository,
                name,
                description,
                isPrerelease,
                publishedDate,
                htmlUrl
            FROM Version_old
            """.trimIndent(),
        )

        val migratedCount = getRowCount(db, "Version")
        Log.i(TAG, "Versions migrated to new table: $migratedCount")
    }

    private fun migrateAssetData(db: SupportSQLiteDatabase) {
        val originalCount = getRowCount(db, "Asset_old")
        Log.i(TAG, "Total assets in old 'Asset' table: $originalCount")

        // Copy data from Asset_old to Asset with default repository
        db.execSQL(
            """
            INSERT OR IGNORE INTO Asset (
                versionTagName,
                repository,
                name,
                size,
                downloadUrl,
                assetId
            )
            SELECT
                versionTagName,
                'wled/WLED' AS repository,
                name,
                size,
                downloadUrl,
                assetId
            FROM Asset_old
            """.trimIndent(),
        )

        val migratedCount = getRowCount(db, "Asset")
        Log.i(TAG, "Assets migrated to new table: $migratedCount")
    }

    private fun dropOldTables(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `Version_old`")
        db.execSQL("DROP TABLE IF EXISTS `Asset_old`")
    }

    private fun createIndices(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Asset_versionTagName` ON `Asset` (`versionTagName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Asset_repository` ON `Asset` (`repository`)")
    }

    private fun getRowCount(db: SupportSQLiteDatabase, tableName: String): Int {
        val cursor = db.query("SELECT COUNT(*) FROM $tableName")
        var count = 0
        if (cursor.moveToFirst()) {
            count = cursor.getInt(0)
        }
        cursor.close()
        return count
    }
}
