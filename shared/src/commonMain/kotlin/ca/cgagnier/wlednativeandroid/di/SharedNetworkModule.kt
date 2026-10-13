package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.defaultJson
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.dsl.module

val sharedNetworkModule: Module = module {
    single<Json> { defaultJson }
    single { DeviceApiFactory(get()) }
    single { WebsocketClientFactory(get(), get(), get(IO_DISPATCHER)) }
    single<GithubApiEndpoints> { KtorGithubApiEndpoints(get()) }
    single { GithubApi(get()) }
}
