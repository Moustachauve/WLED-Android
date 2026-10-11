package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

/**
 * Installs the plugins every WLED device [io.ktor.client.HttpClient] needs, independently of the
 * platform engine (OkHttp on Android, Darwin on iOS).
 */
fun HttpClientConfig<*>.installDeviceApiDefaults(json: Json = defaultJson) {
    install(ContentNegotiation) {
        json(json)
    }
    install(WebSockets) {
        pingInterval = DeviceApiFactory.PING_INTERVAL_SECONDS.seconds
    }
}
