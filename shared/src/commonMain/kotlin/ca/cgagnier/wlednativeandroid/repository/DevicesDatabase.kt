package ca.cgagnier.wlednativeandroid.repository

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.repository.migrations.DbMigration7To8
import ca.cgagnier.wlednativeandroid.repository.migrations.DbMigration8To9
import ca.cgagnier.wlednativeandroid.repository.migrations.MIGRATION_9_10
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

@Database(
    entities = [
        Device::class,
        Repository::class,
        Version::class,
        Asset::class,
    ],
    version = 10,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = DbMigration7To8::class),
        AutoMigration(from = 8, to = 9, spec = DbMigration8To9::class),
    ],
)
@TypeConverters(Converters::class)
@ConstructedBy(DevicesDatabaseConstructor::class)
abstract class DevicesDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun repositoryDao(): RepositoryDao
    abstract fun versionDao(): VersionDao
    abstract fun assetDao(): AssetDao

    companion object
}

@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinRedundantDiagnosticSuppress")
expect object DevicesDatabaseConstructor : RoomDatabaseConstructor<DevicesDatabase> {
    override fun initialize(): DevicesDatabase
}

fun <T : DevicesDatabase> RoomDatabase.Builder<T>.configureDevicesDatabase(): RoomDatabase.Builder<T> = this
    .setDriver(BundledSQLiteDriver())
    .setQueryCoroutineContext(Dispatchers.IO)
    .addMigrations(MIGRATION_9_10)
    .addCallback(object : RoomDatabase.Callback() {
        override fun onCreate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                    INSERT INTO Repository (id, name, ownerAndRepo, description, htmlUrl, isDefault, isEnabled, isUpdateEnabled)
                    VALUES (1, 'WLED', 'wled/WLED', 'Official WLED Repository', 'https://github.com/wled/WLED', 1, 1, 1)
                """.trimIndent(),
            )
        }
    })
