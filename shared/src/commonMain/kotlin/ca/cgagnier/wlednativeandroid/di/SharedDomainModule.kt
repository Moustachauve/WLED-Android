package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.update.UpdateTagResolver
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val sharedDomainModule: Module = module {
    singleOf(::ReleaseService)
    singleOf(::DeviceUpdateManager)
    single<UpdateTagResolver> { get<DeviceUpdateManager>() }
    single { SaveDeviceStateUseCase(get(), get(), get(IO_DISPATCHER)) }
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
        )
    }
}
