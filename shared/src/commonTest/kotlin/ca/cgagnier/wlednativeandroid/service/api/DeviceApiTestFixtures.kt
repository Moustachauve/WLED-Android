package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json

/** Real device json/info from WLED 16.0.1 (anonymized IP/MAC/BSSID). */
internal val SAMPLE_INFO_JSON = """
    {
        "ver": "16.0.1",
        "vid": 2606300,
        "cn": "Niji",
        "release": "ESP32",
        "repo": "wled/WLED",
        "name": "WLED Office",
        "udpport": 21324,
        "ws": 3,
        "fxcount": 220,
        "palcount": 73,
        "arch": "esp32",
        "freeheap": 120936,
        "uptime": 2428926,
        "opt": 79,
        "brand": "WLED",
        "product": "FOSS",
        "mac": "001122334455",
        "ip": "10.0.0.100",
        "leds": { "count": 277, "pwr": 2172, "fps": 43, "maxpwr": 10002, "maxseg": 32, "rgbw": true },
        "wifi": { "bssid": "00:11:22:33:44:55", "rssi": -66, "signal": 68, "channel": 1, "ap": false },
        "fs": { "u": 32, "t": 983, "pmt": 1788492069 }
    }
""".trimIndent()

/** Minimal json/state payload. */
internal const val SAMPLE_STATE_JSON = """{"on":true,"bri":195,"transition":7,"ps":-1,"pl":-1}"""

/**
 * Records every request sent through a [MockEngine] and replies with [handler].
 */
internal class RecordingEngine(
    private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    val requests = mutableListOf<HttpRequestData>()

    val engine = MockEngine { request ->
        requests += request
        handler(request)
    }

    val lastRequest: HttpRequestData get() = requests.last()

    fun client(): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(defaultJson) }
    }
}

internal fun respondingWith(
    content: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    contentType: ContentType = ContentType.Application.Json,
) = RecordingEngine {
    respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, contentType.toString()),
    )
}

/** Thrown from a mock engine to simulate a transport-level failure (e.g. host unreachable). */
internal class FakeTransportException : Exception("Simulated transport failure")
