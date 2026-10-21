package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import kotlinx.serialization.json.Json
import platform.Foundation.NSURLRequestReloadIgnoringLocalAndRemoteCacheData
import platform.Foundation.NSURLSessionConfiguration

/**
 * Creates the [HttpClient] used to talk to WLED devices on iOS, backed by the Ktor Darwin engine
 * (`NSURLSession`).
 *
 * Device responses are never cached: WLED state changes constantly and stale `/json/info`
 * responses would hide address or name changes.
 *
 * @param configureSession Extra configuration applied to the underlying `NSURLSessionConfiguration`
 * after the defaults.
 */
internal fun DeviceApiFactory.Companion.createHttpClient(
    json: Json = defaultJson,
    timeoutSeconds: Long = DeviceApiFactory.DEFAULT_TIMEOUT_SECONDS,
    configureSession: (NSURLSessionConfiguration) -> Unit = {},
): HttpClient {
    // Captured under another name: inside `engine { configureSession { } }` the name resolves to the
    // Darwin engine config's own `configureSession` member instead of this parameter.
    val onConfigureSession = configureSession
    return HttpClient(Darwin) {
        engine {
            configureSession {
                requestCachePolicy = NSURLRequestReloadIgnoringLocalAndRemoteCacheData
                onConfigureSession(this)
            }
        }
        install(HttpTimeout) {
            // The Darwin engine only honours the socket timeout, applied as the NSURLRequest
            // timeoutInterval (an idle timeout); it has no separate connect timeout.
            socketTimeoutMillis = timeoutSeconds * DeviceApiFactory.MILLIS_PER_SECOND
        }
        installDeviceHttpClientDefaults(json)
    }
}

/**
 * Creates a [DeviceApiFactory] backed by the Darwin engine, applying [configureSession] to the
 * underlying `NSURLSessionConfiguration` after the defaults, e.g. to register a stub `URLProtocol`
 * in tests.
 */
fun DeviceApiFactory.Companion.create(configureSession: (NSURLSessionConfiguration) -> Unit): DeviceApiFactory =
    DeviceApiFactory(createHttpClient(configureSession = configureSession))
