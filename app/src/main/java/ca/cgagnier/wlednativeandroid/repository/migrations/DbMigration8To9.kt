package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.DeleteTable
import androidx.room.migration.AutoMigrationSpec


/**
 * Migration from 7->8 introduce the new Device2 table. Migration 8->9 was mostly necessary to then
 * remove the old Device table that is no longer being used. It is done in two step to allow
 * copying data over from the old Device table to the new Device2 table before deleting it.
 */
@DeleteTable(tableName = "Device")
class DbMigration8To9 : AutoMigrationSpec