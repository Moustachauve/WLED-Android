package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeviceApiTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    // Real device json/info from WLED 16.0.1 (anonymized IP/MAC/BSSID)
    private val sampleInfoJson = """
        {
            "ver": "16.0.1",
            "vid": 2606300,
            "cn": "Niji",
            "release": "ESP32",
            "repo": "wled/WLED",
            "name": "WLED Office",
            "udpport": 21324,
            "simplifiedui": false,
            "live": false,
            "liveseg": -1,
            "ws": 3,
            "fxcount": 220,
            "palcount": 73,
            "cpalcount": 1,
            "arch": "esp32",
            "core": "4.4.8.240628",
            "clock": 240,
            "flash": 4,
            "freeheap": 120936,
            "uptime": 2428926,
            "time": "2026-9-25, 00:27:19",
            "opt": 79,
            "brand": "WLED",
            "product": "FOSS",
            "mac": "001122334455",
            "ip": "10.0.0.100",
            "leds": {
                "count": 277,
                "pwr": 2172,
                "fps": 43,
                "maxpwr": 10002,
                "maxseg": 32,
                "rgbw": true
            },
            "wifi": {
                "bssid": "00:11:22:33:44:55",
                "rssi": -66,
                "signal": 68,
                "channel": 1,
                "ap": false
            },
            "fs": {
                "u": 32,
                "t": 983,
                "pmt": 1788492069
            }
        }
    """.trimIndent()

    // Real device json/state from WLED 16.0.1
    private val sampleStateJson = """
        {
            "on": true,
            "bri": 195,
            "transition": 7,
            "ps": -1,
            "pl": -1,
            "nl": {
                "on": false,
                "dur": 60,
                "mode": 1,
                "tbri": 0,
                "rem": -1
            },
            "lor": 0,
            "mainseg": 0,
            "seg": [
                {
                    "id": 0,
                    "start": 0,
                    "stop": 88,
                    "len": 88,
                    "grp": 1,
                    "spc": 0,
                    "on": true,
                    "bri": 255,
                    "col": [[0, 17, 255, 0], [144, 79, 255, 0], [0, 0, 0, 0]],
                    "fx": 107,
                    "sx": 20,
                    "ix": 144,
                    "pal": 3,
                    "sel": false,
                    "rev": false,
                    "mi": false
                }
            ]
        }
    """.trimIndent()

    @Test
    fun `getInfo success returns deserialized Info`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/json/info", request.url.encodedPath)
            respond(
                content = sampleInfoJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)
        val response = deviceApi.getInfo()

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertEquals("WLED Office", response.body?.name)
    }

    @Test
    fun `getInfo error returns error status and errorBody`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)
        val response = deviceApi.getInfo()

        assertFalse(response.isSuccessful)
        assertEquals(500, response.code)
        assertEquals("Internal Server Error", response.errorBody)
    }

    @Test
    fun `postJson sends state and returns updated state`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/json/state", request.url.encodedPath)
            respond(
                content = sampleStateJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)
        val response = deviceApi.postJson(JsonPost(isOn = true, brightness = 195))

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertEquals(195, response.body?.brightness)
    }

    @Test
    fun `updateDevice posts multipart binary and returns response`() = runTest {
        val tempFile = File.createTempFile("wled_test_update", ".bin").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            deleteOnExit()
        }

        val mockEngine = MockEngine { request ->
            assertEquals("/update", request.url.encodedPath)
            respond(
                content = "Update Success! Rebooting...",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        }
        val httpClient = HttpClient(mockEngine)

        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)
        val response = deviceApi.updateDevice(tempFile)

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertEquals("Update Success! Rebooting...", response.body)
        assertNull(response.errorBody)
    }

    @Test
    fun `updateDevice error returns error status and errorBody`() = runTest {
        val tempFile = File.createTempFile("wled_test_update_fail", ".bin").apply {
            writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            deleteOnExit()
        }

        val mockEngine = MockEngine { _ ->
            respond(
                content = "Update Failed: Not enough space",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val httpClient = HttpClient(mockEngine)

        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)
        val response = deviceApi.updateDevice(tempFile)

        assertFalse(response.isSuccessful)
        assertEquals(500, response.code)
        assertNull(response.body)
        assertEquals("Update Failed: Not enough space", response.errorBody)
    }

    @Test
    fun `DeviceApiFactory creates device API with normalized url`() {
        val factory = DeviceApiFactory(OkHttpClient(), testJson)
        val device = Device(macAddress = "AABBCCDDEEFF", address = "10.0.0.100")

        val apiFromAddress = factory.create("10.0.0.100")
        assertNotNull(apiFromAddress)

        val apiFromDevice = factory.create(device)
        assertNotNull(apiFromDevice)

        val apiWithTimeout = factory.create(device, 60L)
        assertNotNull(apiWithTimeout)
    }
}
