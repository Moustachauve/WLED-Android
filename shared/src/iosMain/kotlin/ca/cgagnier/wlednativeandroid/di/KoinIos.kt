package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.USER_PREFERENCES_DATA_STORE_FILE_NAME
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.createDatabase
import ca.cgagnier.wlednativeandroid.repository.createInMemoryDatabase
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.repository.documentDirectory
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import io.ktor.client.HttpClient
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.module

internal fun createIosPlatformModule(
    database: DevicesDatabase = DevicesDatabase.createDatabase(),
    preferencesPathProducer: () -> Path = {
        (documentDirectory() + "/" + USER_PREFERENCES_DATA_STORE_FILE_NAME).toPath()
    },
): Module = module {
    includes(sharedModules)
    single<DevicesDatabase> { database }
    single<DataStore<UserPreferences>> {
        createUserPreferencesDataStore(
            producePath = preferencesPathProducer,
        )
    }
    single<HttpClient> { DeviceApiFactory.createHttpClient(json = get()) }
}

internal val iosPlatformModule = createIosPlatformModule()

fun initKoin() {
    startKoin {
        modules(iosPlatformModule)
    }
}

internal fun initKoinForTest(
    database: DevicesDatabase = DevicesDatabase.createInMemoryDatabase(),
    preferencesPathProducer: () -> Path,
) {
    startKoin {
        modules(
            createIosPlatformModule(
                database = database,
                preferencesPathProducer = preferencesPathProducer,
            ),
        )
    }
}

object KoinHelper : KoinComponent {
    val deviceRepository: DeviceRepository by inject()
    val userPreferencesRepository: UserPreferencesRepository by inject()
}
