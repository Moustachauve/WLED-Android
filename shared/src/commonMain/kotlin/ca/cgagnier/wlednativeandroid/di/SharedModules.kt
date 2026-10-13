package ca.cgagnier.wlednativeandroid.di

import org.koin.core.module.Module

val sharedModules: List<Module> = listOf(
    sharedCoroutinesModule,
    sharedNetworkModule,
    sharedDatabaseModule,
    sharedDomainModule,
)
