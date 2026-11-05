package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import ca.cgagnier.wlednativeandroid.repository.migrations.UserPreferencesV0ToV1
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import okio.FileSystem
import okio.Path
import ca.cgagnier.wlednativeandroid.shared.fileSystem as defaultFileSystem

const val USER_PREFERENCES_DATA_STORE_FILE_NAME = "user_preferences.json"

private val logger = Logger.withTag("UserPreferencesDataStore")

// Factory exposes default-valued configuration hooks for DataStore customization, DI, and test doubles
@Suppress("LongParameterList")
fun createUserPreferencesDataStore(
    producePath: () -> Path,
    fileSystem: FileSystem = defaultFileSystem,
    serializer: OkioSerializer<UserPreferences> = UserPreferencesSerializer(),
    corruptionHandler: ReplaceFileCorruptionHandler<UserPreferences>? = ReplaceFileCorruptionHandler { exception ->
        logger.w(exception) { "User preferences corrupted, falling back to default preferences" }
        UserPreferences()
    },
    migrations: List<DataMigration<UserPreferences>> = listOf(UserPreferencesV0ToV1()),
    scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
): DataStore<UserPreferences> = DataStoreFactory.create(
    storage = OkioStorage(
        fileSystem = fileSystem,
        serializer = serializer,
        producePath = producePath,
    ),
    corruptionHandler = corruptionHandler,
    migrations = migrations,
    scope = scope,
)
