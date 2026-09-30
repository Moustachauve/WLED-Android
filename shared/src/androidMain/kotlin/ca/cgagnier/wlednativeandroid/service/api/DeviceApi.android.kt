package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.pingInterval
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File
import kotlin.time.Duration.Companion.seconds

suspend fun DeviceApi.updateDevice(binaryFile: File): ApiResponse<String> =
    updateDevice(binaryFile.readBytes(), binaryFile.name)

fun DeviceApiFactory.Companion.createHttpClient(okHttpClient: OkHttpClient, json: Json = defaultJson): HttpClient =
    HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(WebSockets) {
            pingInterval = DeviceApiFactory.PING_INTERVAL_SECONDS.seconds
        }
    }

operator fun DeviceApiFactory.Companion.invoke(
    okHttpClient: OkHttpClient,
    json: Json = defaultJson,
): DeviceApiFactory = DeviceApiFactory(createHttpClient(okHttpClient, json))
