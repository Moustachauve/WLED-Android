package ca.cgagnier.wlednativeandroid.repository.migrations

import android.util.Log
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

private const val TAG = "DbMigration7To8"

class DbMigration7To8 : AutoMigrationSpec {
    override fun onPostMigrate(connection: SQLiteConnection) {
        Log.i(TAG, "onPostMigrate starting")

        val originalDeviceCount = connection.prepare("SELECT COUNT(*) FROM device").use { stmt ->
            if (stmt.step()) stmt.getInt(0) else 0
        }
        Log.i(TAG, "Total devices in old 'device' table: $originalDeviceCount")

        // Log the count of devices that can be migrated
        val devicesToMigrateCount = connection.prepare(
            "SELECT COUNT(*) FROM device WHERE macAddress IS NOT NULL AND macAddress != '__unknown__'",
        ).use { stmt ->
            if (stmt.step()) stmt.getInt(0) else 0
        }
        Log.i(TAG, "Number of devices to be migrated: $devicesToMigrateCount")

        // Copy data from legacy Device to Device2
        // We filter out devices with unknown MAC addresses because 'macAddress'
        // is the Primary Key in the new table and must be unique/valid
        connection.execSQL(
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
            """.trimIndent(),
        )

        // Log the count of devices inserted into the new table
        val insertedCount = connection.prepare("SELECT COUNT(*) FROM Device2").use { stmt ->
            if (stmt.step()) stmt.getInt(0) else 0
        }
        Log.i(TAG, "Number of devices successfully inserted into 'Device2': $insertedCount")

        Log.i(TAG, "onPostMigrate done! Migration is complete.")
    }
}
