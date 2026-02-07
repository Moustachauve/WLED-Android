package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec

/**
 * Migration from 10->11 removes the old Version and Asset tables after data has been migrated
 * to the new schema with repository tracking support.
 */
@DeleteTable(tableName = "Version_old")
@DeleteTable(tableName = "Asset_old")
class DbMigration10To11 : AutoMigrationSpec
