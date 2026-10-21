package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.domain.AddressValidator
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.USER_PREFERENCES_DATA_STORE_FILE_NAME
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.repository.createDatabase
import ca.cgagnier.wlednativeandroid.repository.createInMemoryDatabase
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.repository.documentDirectory
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManagerConfig
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.IosNetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.NoOpDeviceConnectionFactory
import io.ktor.client.HttpClient
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.Koin
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.dsl.onClose
import kotlin.random.Random

internal val iosPlatformModule
    get() = module {
        includes(sharedModules)
        single<DevicesDatabase> { DevicesDatabase.createDatabase() }
        single<DataStore<UserPreferences>> {
            createUserPreferencesDataStore(
                producePath = { (documentDirectory() + "/" + USER_PREFERENCES_DATA_STORE_FILE_NAME).toPath() },
            )
        }
        single<HttpClient> { DeviceApiFactory.createHttpClient(json = get()) } onClose { it?.close() }
        single<NetworkConnectivityManager> { IosNetworkConnectivityManager(get()) }
    }

/**
 * Isolated Koin dependency container for the iOS app.
 *
 * Production uses [KoinContainer.production] with on-disk Room and DataStore persistence.
 * Tests and SwiftUI previews use [KoinContainer.inMemory] to spin up isolated `koinApplication`
 * instances backed by an in-memory [DevicesDatabase] and optional test overrides.
 */
class KoinContainer private constructor(
    private val koinApplication: KoinApplication,
    private val tempPreferencesPath: Path? = null,
) {
    internal val koin: Koin get() = koinApplication.koin

    val database: DevicesDatabase get() = koin.get()
    val deviceRepository: DeviceRepository get() = koin.get()
    val repositoryDao: RepositoryDao get() = koin.get()
    val versionWithAssetsRepository: VersionWithAssetsRepository get() = koin.get()
    val userPreferencesRepository: UserPreferencesRepository get() = koin.get()
    val deviceConnectionManager: DeviceConnectionManager get() = koin.get()
    val deviceFirstContactService: DeviceFirstContactService get() = koin.get()
    val addressValidator: AddressValidator get() = koin.get()
    val releaseService: ReleaseService get() = koin.get()
    val deviceUpdateManager: DeviceUpdateManager get() = koin.get()
    val githubApi: GithubApi get() = koin.get()
    val deviceApiFactory: DeviceApiFactory get() = koin.get()

    fun close() {
        koinApplication.close()
        tempPreferencesPath?.let { FileSystem.SYSTEM.delete(it, mustExist = false) }
    }

    companion object {
        fun production(): KoinContainer = KoinContainer(
            koinApplication {
                modules(iosPlatformModule)
            },
        )

        fun inMemory(
            connectionFactory: DeviceConnectionFactory? = null,
            config: DeviceConnectionManagerConfig? = null,
            deviceApiFactory: DeviceApiFactory? = null,
        ): KoinContainer {
            val tempPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "user_prefs_${Random.nextLong()}.json"
            val app = koinApplication {
                modules(
                    iosPlatformModule,
                    module {
                        single<DevicesDatabase> { DevicesDatabase.createInMemoryDatabase() }
                        single<DataStore<UserPreferences>> {
                            createUserPreferencesDataStore(producePath = { tempPath })
                        }
                        single<DeviceConnectionFactory> { connectionFactory ?: NoOpDeviceConnectionFactory }
                        if (config != null) {
                            single { config }
                        }
                        if (deviceApiFactory != null) {
                            single { deviceApiFactory }
                        }
                    },
                )
            }
            return KoinContainer(app, tempPath)
        }
    }
}
