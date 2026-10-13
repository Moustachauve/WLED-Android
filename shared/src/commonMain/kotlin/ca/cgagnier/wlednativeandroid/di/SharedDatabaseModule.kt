package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.repository.AssetDao
import ca.cgagnier.wlednativeandroid.repository.DeviceDao
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.VersionDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedDatabaseModule: Module = module {
    single<DeviceDao> { get<DevicesDatabase>().deviceDao() }
    single<VersionDao> { get<DevicesDatabase>().versionDao() }
    single<RepositoryDao> { get<DevicesDatabase>().repositoryDao() }
    single<AssetDao> { get<DevicesDatabase>().assetDao() }
    single { DeviceRepository(get()) }
    single { VersionWithAssetsRepository(get(), get(), get(), get()) }
    single { UserPreferencesRepository(get()) }
}
