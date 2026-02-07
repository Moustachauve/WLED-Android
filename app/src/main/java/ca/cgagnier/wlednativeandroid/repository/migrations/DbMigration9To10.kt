package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.room.RenameTable
import androidx.room.migration.AutoMigrationSpec

/**
 * Migration from 9->10 adds repository information to Version and Asset tables
 * to support tracking releases from multiple WLED repositories/forks.
 * 
 * We rename the old tables, create new ones with repository field (defaulting to "wled/WLED"),
 * then drop the old tables. This preserves existing data while adding the new repository tracking.
 */
@RenameTable(fromTableName = "Version", toTableName = "Version_old")
@RenameTable(fromTableName = "Asset", toTableName = "Asset_old")
class DbMigration9To10 : AutoMigrationSpec
