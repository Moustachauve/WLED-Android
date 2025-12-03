package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "DbMigration7To8"

class DbMigration7To8 : AutoMigrationSpec {
    override fun onPostMigrate(db: SupportSQLiteDatabase) {
        Log.i(TAG, "onPostMigrate starting")

        val originalDeviceCountCursor = db.query("SELECT COUNT(*) FROM device")
        var originalDeviceCount = 0
        if (originalDeviceCountCursor.moveToFirst()) {
            originalDeviceCount = originalDeviceCountCursor.getInt(0)
        }
        originalDeviceCountCursor.close()
        Log.i(TAG, "Total devices in old 'device' table: $originalDeviceCount")

        // Log the count of devices that can be migrated
        val devicesToMigrateCursor =
            db.query("SELECT COUNT(*) FROM device WHERE macAddress IS NOT NULL AND macAddress != '__unknown__'")
        var devicesToMigrateCount = 0
        if (devicesToMigrateCursor.moveToFirst()) {
            devicesToMigrateCount = devicesToMigrateCursor.getInt(0)
        }
        devicesToMigrateCursor.close()
        Log.i(TAG, "Number of devices to be migrated: $devicesToMigrateCount")


        // Copy data from legacy Device to Device2
        // We filter out devices with unknown MAC addresses because 'macAddress'
        // is the Primary Key in the new table and must be unique/valid
        db.execSQL(
            """
            INSERT OR IGNORE INTO Device2 (
                macAddress, 
                address, 
                isHidden, 
                customName, 
                originalName, 
                skipUpdateTag, 
                branch, 
                lastSeen
            )
            SELECT 
                macAddress, 
                address, 
                isHidden, 
                CASE WHEN isCustomName = 1 THEN name ELSE '' END, 
                CASE WHEN isCustomName = 0 THEN name ELSE '' END,
                '',
                'UNKNOWN',
                0
            FROM device
            WHERE macAddress IS NOT NULL AND macAddress != '__unknown__'
        """.trimIndent()
        )

        // Log the count of devices inserted into the new table
        val insertedDevicesCursor = db.query("SELECT COUNT(*) FROM Device2")
        var insertedCount = 0
        if (insertedDevicesCursor.moveToFirst()) {
            insertedCount = insertedDevicesCursor.getInt(0)
        }
        insertedDevicesCursor.close()
        Log.i(TAG, "Number of devices successfully inserted into 'Device2': $insertedCount")



        Log.i(TAG, "onPostMigrate done! Migration is complete.")
    }
}