package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.streams.asInput
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.io.File

interface DeviceApi {
    suspend fun getInfo(): ApiResponse<Info>

    suspend fun postJson(state: JsonPost): ApiResponse<State>

    suspend fun updateDevice(binaryFile: File): ApiResponse<String>
}

class KtorDeviceApi(private val baseUrl: String, private val httpClient: HttpClient) : DeviceApi {

    private fun normalizeUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val relative = if (path.startsWith("/")) path.drop(1) else path
        return "$base$relative"
    }

    override suspend fun getInfo(): ApiResponse<Info> {
        val response = httpClient.get(normalizeUrl("json/info"))
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<Info>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    override suspend fun postJson(state: JsonPost): ApiResponse<State> {
        val response = httpClient.post(normalizeUrl("json/state")) {
            contentType(ContentType.Application.Json)
            setBody(state)
        }
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<State>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    override suspend fun updateDevice(binaryFile: File): ApiResponse<String> {
        val response = httpClient.submitFormWithBinaryData(
            url = normalizeUrl("update"),
            formData = formData {
                append(
                    key = "file",
                    value = InputProvider(size = binaryFile.length()) {
                        binaryFile.inputStream().asInput()
                    },
                    headers = Headers.build {
                        append(HttpHeaders.ContentType, "application/octet-stream")
                        append(HttpHeaders.ContentDisposition, "filename=\"${binaryFile.name}\"")
                    },
                )
            },
        )
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = text)
        } else {
            ApiResponse(code = response.status.value, errorBody = text)
        }
    }
}

private val defaultJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * Factory for creating instances of DeviceApi.
 *
 * Since the base URL is dynamic per device, we can't provide a singleton instance.
 * Instead, we provide this factory to create a new DeviceApi on-demand.
 *
 * @param client The OkHttpClient engine to use for the underlying HTTP transport.
 * @param json The Json instance to use for serialization/deserialization.
 */
class DeviceApiFactory(
    private val client: OkHttpClient,
    private val json: Json = defaultJson,
    private val sharedHttpClient: HttpClient? = null,
) {
    private val defaultHttpClient: HttpClient by lazy {
        sharedHttpClient ?: createHttpClient(client, json)
    }

    /**
     * Create a new DeviceApi instance from a device address.
     *
     * @param address The address of a device to create the API for.
     */
    fun create(address: String): DeviceApi {
        val baseUrl = normalizeAddress(address)
        return KtorDeviceApi(baseUrl, defaultHttpClient)
    }

    /**
     * Create a new DeviceApi instance for a device.
     *
     * @param device The device to create the API for.
     */
    fun create(device: Device): DeviceApi = create(device.getDeviceUrl())

    private val timeoutClients = java.util.concurrent.ConcurrentHashMap<Long, HttpClient>()

    /**
     * Create a new DeviceApi instance with a custom timeout.
     *
     * HttpClient.config shares the parent client's OkHttp engine, connection pool, and threads
     * while applying custom timeout configuration. Configured clients are cached per timeout
     * to avoid redundant allocations.
     *
     * @param device The device to create the API for.
     * @param timeout The custom timeout in seconds.
     */
    fun create(device: Device, timeout: Long): DeviceApi {
        val timeoutMillis = timeout * MILLIS_PER_SECOND
        val customHttpClient = timeoutClients.computeIfAbsent(timeoutMillis) { ms ->
            defaultHttpClient.config {
                install(HttpTimeout) {
                    requestTimeoutMillis = ms
                    connectTimeoutMillis = ms
                    socketTimeoutMillis = ms
                }
            }
        }
        return KtorDeviceApi(normalizeAddress(device.getDeviceUrl()), customHttpClient)
    }

    private fun normalizeAddress(address: String): String =
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            "http://$address/"
        } else {
            address
        }

    companion object {
        private const val MILLIS_PER_SECOND = 1000L

        fun createHttpClient(okHttpClient: OkHttpClient, json: Json = defaultJson): HttpClient = HttpClient(OkHttp) {
            engine {
                preconfigured = okHttpClient
            }
            install(ContentNegotiation) {
                json(json)
            }
        }
    }
}
