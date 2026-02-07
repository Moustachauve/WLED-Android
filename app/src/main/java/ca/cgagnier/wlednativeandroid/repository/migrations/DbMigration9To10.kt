package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "DbMigration9To10"

/**
 * Migration from 9->10 adds repository information to Version and Asset tables
 * to support tracking releases from multiple WLED repositories/forks.
 * 
 * We rename the old tables, create new ones with repository field,
 * copy existing data with default repository "wled/WLED", then drop the old tables.
 */
@RenameTable(fromTableName = "Version", toTableName = "Version_old")
@RenameTable(fromTableName = "Asset", toTableName = "Asset_old")
class DbMigration9To10 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "onPostMigrate starting - migrating Version and Asset data")
        
        // Migrate Version table
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
            """.trimIndent()
        )
        
        val migratedVersionCountCursor = db.query("SELECT COUNT(*) FROM Version")
        var migratedVersionCount = 0
        if (migratedVersionCountCursor.moveToFirst()) {
            migratedVersionCount = migratedVersionCountCursor.getInt(0)
        }
        migratedVersionCountCursor.close()
        Log.i(TAG, "Versions migrated to new table: $migratedVersionCount")
        
        // Migrate Asset table
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
            """.trimIndent()
        )
        
        val migratedAssetCountCursor = db.query("SELECT COUNT(*) FROM Asset")
        var migratedAssetCount = 0
        if (migratedAssetCountCursor.moveToFirst()) {
            migratedAssetCount = migratedAssetCountCursor.getInt(0)
        }
        migratedAssetCountCursor.close()
        Log.i(TAG, "Assets migrated to new table: $migratedAssetCount")
        
        Log.i(TAG, "onPostMigrate done! Migration is complete.")
    }
}
