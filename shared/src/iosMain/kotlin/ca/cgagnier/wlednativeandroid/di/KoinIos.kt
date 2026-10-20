package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.USER_PREFERENCES_DATA_STORE_FILE_NAME
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.repository.createDatabase
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.repository.documentDirectory
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.IosNetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.dsl.module

internal val iosPlatformModule = module {
    includes(sharedModules)
    single<DevicesDatabase> { DevicesDatabase.createDatabase() }
    single<DataStore<UserPreferences>> {
        createUserPreferencesDataStore(
            producePath = { (documentDirectory() + "/" + USER_PREFERENCES_DATA_STORE_FILE_NAME).toPath() },
        )
    }
    single<HttpClient> { DeviceApiFactory.createHttpClient(json = get()) }
    single<NetworkConnectivityManager> { IosNetworkConnectivityManager(get()) }
}

fun initKoin() {
    startKoin {
        modules(iosPlatformModule)
    }
}

object KoinHelper : KoinComponent {
    val deviceRepository: DeviceRepository get() = get()
    val repositoryDao: RepositoryDao get() = get()
    val versionWithAssetsRepository: VersionWithAssetsRepository get() = get()
    val userPreferencesRepository: UserPreferencesRepository get() = get()
    val deviceConnectionManager: DeviceConnectionManager get() = get()
    val releaseService: ReleaseService get() = get()
    val deviceUpdateManager: DeviceUpdateManager get() = get()
    val githubApi: GithubApi get() = get()
    val deviceApiFactory: DeviceApiFactory get() = get()
}
