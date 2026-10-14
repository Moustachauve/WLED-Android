package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.USER_PREFERENCES_DATA_STORE_FILE_NAME
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.createDatabase
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.repository.documentDirectory
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.dsl.module

internal val iosPlatformModule = module {
    single<DevicesDatabase> { DevicesDatabase.createDatabase() }
    single<DataStore<UserPreferences>> {
        createUserPreferencesDataStore(
            producePath = { (documentDirectory() + "/" + USER_PREFERENCES_DATA_STORE_FILE_NAME).toPath() },
        )
    }
    single<HttpClient> { DeviceApiFactory.createHttpClient(json = get()) }
}

fun initKoin() {
    startKoin {
        modules(sharedModules + iosPlatformModule)
    }
}
