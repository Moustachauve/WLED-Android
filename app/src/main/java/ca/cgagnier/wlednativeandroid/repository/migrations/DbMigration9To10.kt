package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "DbMigration9To10"
private const val FROM_VERSION = 9
private const val TO_VERSION = 10

/**
 * Migration from 9->10 adds the Repository table and updates Device, Version, and Asset tables
 * to support tracking releases from multiple WLED repositories/forks using Long surrogate keys.
 *
 * It creates the Repository table and inserts the default 'wled/WLED' repository (ID 1).
 * For Device2: adds repositoryId column with default value 1.
 * For Version/Asset: renames old tables, creates new ones with foreign keys,
 * copies existing data linking back to Repository ID 1, then drops old tables.
 */
val MIGRATION_9_10 = object : Migration(FROM_VERSION, TO_VERSION) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Starting migration from 9 to 10")

        createRepositoryTable(db)
        insertDefaultRepositories(db)
        addRepositoryToDevice(db)
        renameOldTables(db)
        createNewTables(db)
        migrateVersionData(db)
        migrateAssetData(db)
        dropOldTables(db)
        createIndices(db)

        Log.i(TAG, "Migration from 9 to 10 complete!")
    }

    private fun createRepositoryTable(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Repository` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `ownerAndRepo` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `htmlUrl` TEXT NOT NULL,
                `isDefault` INTEGER NOT NULL DEFAULT 0,
                `isEnabled` INTEGER NOT NULL DEFAULT 1,
                `isUpdateEnabled` INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Repository_ownerAndRepo` ON `Repository` (`ownerAndRepo`)")
    }

    private fun insertDefaultRepositories(db: SupportSQLiteDatabase) {
        // Insert wled/WLED as ID 1 to match default values
        db.execSQL(
            """
            INSERT OR IGNORE INTO Repository (id, name, ownerAndRepo, description, htmlUrl, isDefault, isEnabled, isUpdateEnabled)
            VALUES (1, 'WLED', 'wled/WLED', 'WLED Firmware', 'https://github.com/wled/WLED', 1, 1, 1)
            """.trimIndent(),
        )
    }

    private fun addRepositoryToDevice(db: SupportSQLiteDatabase) {
        // Add repositoryId column to Device2 table with default value 1
        db.execSQL("ALTER TABLE `Device2` ADD COLUMN `repositoryId` INTEGER NOT NULL DEFAULT 1")
        Log.i(TAG, "Added repositoryId column to Device2 table")
    }

    private fun renameOldTables(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `Version` RENAME TO `Version_old`")
        db.execSQL("ALTER TABLE `Asset` RENAME TO `Asset_old`")
    }

    private fun createNewTables(db: SupportSQLiteDatabase) {
        // Create new Version table with repositoryId foreign key
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Version` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `repositoryId` INTEGER NOT NULL,
                `tagName` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `description` TEXT NOT NULL,
                `isPrerelease` INTEGER NOT NULL,
                `publishedDate` TEXT NOT NULL,
                `htmlUrl` TEXT NOT NULL,
                FOREIGN KEY(`repositoryId`) REFERENCES `Repository`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        // Create new Asset table with versionId foreign key
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `Asset` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `versionId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `size` INTEGER NOT NULL,
                `downloadUrl` TEXT NOT NULL,
                `assetId` INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY(`versionId`) REFERENCES `Version`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    private fun migrateVersionData(db: SupportSQLiteDatabase) {
        val originalCount = getRowCount(db, "Version_old")
        Log.i(TAG, "Total versions in old 'Version' table: $originalCount")

        // Copy data from Version_old to Version with default repositoryId 1
        db.execSQL(
            """
            INSERT INTO Version (
                repositoryId,
                tagName,
                name,
                description,
                isPrerelease,
                publishedDate,
                htmlUrl
            )
            SELECT
                1 AS repositoryId,
                tagName,
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

        // Copy data from Asset_old to Asset joining on Version to get the new versionId
        db.execSQL(
            """
            INSERT INTO Asset (
                versionId,
                name,
                size,
                downloadUrl,
                assetId
            )
            SELECT
                v.id AS versionId,
                a.name,
                a.size,
                a.downloadUrl,
                a.assetId
            FROM Asset_old a
            INNER JOIN Version v ON v.tagName = a.versionTagName AND v.repositoryId = 1
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
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_Version_repositoryId_tagName` ON `Version` (`repositoryId`, `tagName`)",
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS " +
                "`index_Asset_versionId_name` ON `Asset` (`versionId`, `name`)",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_Asset_versionId` ON `Asset` (`versionId`)")
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
