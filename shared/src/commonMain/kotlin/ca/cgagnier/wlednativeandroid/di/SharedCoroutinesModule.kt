package ca.cgagnier.wlednativeandroid.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val DEFAULT_DISPATCHER = named("defaultDispatcher")
val IO_DISPATCHER = named("ioDispatcher")

internal val sharedCoroutinesModule: Module = module {
    single<CoroutineDispatcher>(DEFAULT_DISPATCHER) { Dispatchers.Default }
    single<CoroutineDispatcher>(IO_DISPATCHER) { Dispatchers.IO }
    single { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(DEFAULT_DISPATCHER)) }
}
