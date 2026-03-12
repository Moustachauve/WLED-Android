package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "DbMigration9To10"

/**
 * Migration from 9->10 adds repository information to Version and Asset tables
 * to support tracking releases from multiple WLED repositories/forks.
 *
 * We rename the old tables, create new ones with repository field,
 * copy existing data with default repository "wled/WLED", then drop the old tables.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Starting migration from 9 to 10")

        // Rename old tables
        db.execSQL("ALTER TABLE `Version` RENAME TO `Version_old`")
        db.execSQL("ALTER TABLE `Asset` RENAME TO `Asset_old`")

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

        // Migrate Version data
        val originalVersionCountCursor = db.query("SELECT COUNT(*) FROM Version_old")
        var originalVersionCount = 0
        if (originalVersionCountCursor.moveToFirst()) {
            originalVersionCount = originalVersionCountCursor.getInt(0)
        }
        originalVersionCountCursor.close()
        Log.i(TAG, "Total versions in old 'Version' table: $originalVersionCount")

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

        val migratedVersionCountCursor = db.query("SELECT COUNT(*) FROM Version")
        var migratedVersionCount = 0
        if (migratedVersionCountCursor.moveToFirst()) {
            migratedVersionCount = migratedVersionCountCursor.getInt(0)
        }
        migratedVersionCountCursor.close()
        Log.i(TAG, "Versions migrated to new table: $migratedVersionCount")

        // Migrate Asset data
        val originalAssetCountCursor = db.query("SELECT COUNT(*) FROM Asset_old")
        var originalAssetCount = 0
        if (originalAssetCountCursor.moveToFirst()) {
            originalAssetCount = originalAssetCountCursor.getInt(0)
        }
        originalAssetCountCursor.close()
        Log.i(TAG, "Total assets in old 'Asset' table: $originalAssetCount")

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

        val migratedAssetCountCursor = db.query("SELECT COUNT(*) FROM Asset")
        var migratedAssetCount = 0
        if (migratedAssetCountCursor.moveToFirst()) {
            migratedAssetCount = migratedAssetCountCursor.getInt(0)
        }
        migratedAssetCountCursor.close()
        Log.i(TAG, "Assets migrated to new table: $migratedAssetCount")

        // Drop old tables
        db.execSQL("DROP TABLE IF EXISTS `Version_old`")
        db.execSQL("DROP TABLE IF EXISTS `Asset_old`")

        // Create indices for Asset table (after data migration)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Asset_versionTagName` ON `Asset` (`versionTagName`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Asset_repository` ON `Asset` (`repository`)")

        Log.i(TAG, "Migration from 9 to 10 complete!")
    }
}
