package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedDomainModule: Module = module {
    single { ReleaseService(get(), get()) }
    single { DeviceUpdateManager(get()) }
    single { SaveDeviceStateUseCase(get(), get(), get(IO_DISPATCHER)) }
}
