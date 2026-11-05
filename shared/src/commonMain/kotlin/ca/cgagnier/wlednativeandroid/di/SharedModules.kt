package ca.cgagnier.wlednativeandroid.di

import org.koin.core.module.Module

val sharedModules: List<Module>
    get() = listOf(
        sharedCoroutinesModule,
        sharedNetworkModule,
        sharedDatabaseModule,
        sharedDomainModule,
    )
