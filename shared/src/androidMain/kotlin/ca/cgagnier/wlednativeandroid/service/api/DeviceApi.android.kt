package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

suspend fun DeviceApi.updateDevice(binaryFile: File): ApiResponse<String> =
    updateDevice(binaryFile.readBytes(), binaryFile.name)

fun DeviceApiFactory.Companion.createHttpClient(okHttpClient: OkHttpClient, json: Json = defaultJson): HttpClient =
    HttpClient(OkHttp) {
        engine {
            preconfigured = okHttpClient
        }
        installDeviceApiDefaults(json)
    }

operator fun DeviceApiFactory.Companion.invoke(
    okHttpClient: OkHttpClient,
    json: Json = defaultJson,
): DeviceApiFactory = DeviceApiFactory(createHttpClient(okHttpClient, json))
