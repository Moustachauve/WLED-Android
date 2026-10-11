package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json
import platform.Foundation.NSURLRequestReloadIgnoringLocalAndRemoteCacheData
import platform.Foundation.NSURLSessionConfiguration

/** Connect and socket timeout for device requests, aligned with the Android OkHttp client. */
const val DEFAULT_IOS_TIMEOUT_SECONDS = 30L

/**
 * Creates the [HttpClient] used to talk to WLED devices on iOS, backed by the Ktor Darwin engine
 * (`NSURLSession`).
 *
 * Device responses are never cached: WLED state changes constantly and stale `/json/info`
 * responses would hide address or name changes.
 *
 * @param configureSession Extra configuration applied to the underlying `NSURLSessionConfiguration`
 * after the defaults. Mainly a test seam, e.g. to register a stub `URLProtocol`.
 */
fun DeviceApiFactory.Companion.createHttpClient(
    json: Json = defaultJson,
    timeoutSeconds: Long = DEFAULT_IOS_TIMEOUT_SECONDS,
    configureSession: (NSURLSessionConfiguration) -> Unit = {},
): HttpClient = HttpClient(Darwin) {
    engine {
        configureSession {
            requestCachePolicy = NSURLRequestReloadIgnoringLocalAndRemoteCacheData
            configureSession(this)
        }
    }
    install(HttpTimeout) {
        val timeoutMillis = timeoutSeconds * DeviceApiFactory.MILLIS_PER_SECOND
        connectTimeoutMillis = timeoutMillis
        socketTimeoutMillis = timeoutMillis
    }
    installDeviceApiDefaults(json)
}

/**
 * Creates a [DeviceApiFactory] backed by the Darwin engine with the default configuration.
 *
 * Callers should create one instance and share it so all requests reuse the same `NSURLSession`.
 */
fun DeviceApiFactory.Companion.create(): DeviceApiFactory = DeviceApiFactory(createHttpClient())

/**
 * Creates a [DeviceApiFactory] backed by the Darwin engine, applying [configureSession] to the
 * underlying `NSURLSessionConfiguration`.
 *
 * Separate from the no-argument overload because Kotlin default arguments are not visible from Swift.
 */
fun DeviceApiFactory.Companion.create(configureSession: (NSURLSessionConfiguration) -> Unit): DeviceApiFactory =
    DeviceApiFactory(createHttpClient(configureSession = configureSession))
