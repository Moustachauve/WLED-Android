package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "DbMigration10To11"

/**
 * Migration from 10->11 removes the old Version and Asset tables after data has been migrated
 * to the new schema with repository tracking support.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "Starting migration from 10 to 11")

        // Drop the old tables if they exist (cleanup from previous migration)
        db.execSQL("DROP TABLE IF EXISTS Version_old")
        db.execSQL("DROP TABLE IF EXISTS Asset_old")

        Log.i(TAG, "Migration from 10 to 11 complete!")
    }
}
