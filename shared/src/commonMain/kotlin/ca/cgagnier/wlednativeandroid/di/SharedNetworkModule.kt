package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.defaultJson
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val sharedNetworkModule: Module = module {
    single<Json> { defaultJson }
    singleOf(::DeviceApiFactory)
    single { WebsocketClientFactory(get(), get(), get(IO_DISPATCHER)) }
    single<GithubApiEndpoints> { KtorGithubApiEndpoints(get()) }
    singleOf(::GithubApi)
}
