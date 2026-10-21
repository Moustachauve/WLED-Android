package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.domain.AddressValidator
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManagerConfig
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.update.UpdateTagResolver
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

internal val sharedDomainModule: Module
    get() = module {
        singleOf(::AddressValidator)
        single { DeviceFirstContactService(get(), get(), get()) }
        single { ReleaseService(get(), get(), get(IO_DISPATCHER)) }
        singleOf(::DeviceUpdateManager)
        single<UpdateTagResolver> { get<DeviceUpdateManager>() }
        single { DeviceConnectionManagerConfig() }
        single {
            DeviceConnectionManager(
                store = get(),
                connectionFactory = get(),
                connectivity = get(),
                updateTagResolver = get(),
                repositoryIdResolver = get(),
                coroutineScope = get(),
                backgroundDispatcher = get(DEFAULT_DISPATCHER),
                ioDispatcher = get(IO_DISPATCHER),
                config = get(),
            )
        } onClose {
            it?.destroy()
        }
    }
