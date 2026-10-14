package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val sharedDomainModule: Module = module {
    singleOf(::ReleaseService)
    singleOf(::DeviceUpdateManager)
    single { SaveDeviceStateUseCase(get(), get(), get(IO_DISPATCHER)) }
}
