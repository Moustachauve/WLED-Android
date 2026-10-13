package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.createDatabase
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import io.ktor.client.HttpClient
import org.koin.core.context.startKoin
import org.koin.dsl.module

val iosPlatformModule = module {
    single<DevicesDatabase> { DevicesDatabase.createDatabase() }
    single<HttpClient> { DeviceApiFactory.createHttpClient() }
}

fun initKoin() = startKoin {
    modules(sharedModules + iosPlatformModule)
}
