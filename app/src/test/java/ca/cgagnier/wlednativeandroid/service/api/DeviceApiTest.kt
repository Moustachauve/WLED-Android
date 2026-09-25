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

    private val sampleInfoJson = """
        {
            "name": "Living Room LED",
            "ver": "0.14.0",
            "mac": "aabbccddeeff",
            "leds": { "count": 60 },
            "wifi": { "rssi": -65 }
        }
    """.trimIndent()

    private val sampleStateJson = """
        {
            "on": true,
            "bri": 128
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

        val deviceApi = KtorDeviceApi("http://192.168.1.50/", httpClient)
        val response = deviceApi.getInfo()

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertNotNull(response.body)
        assertEquals("Living Room LED", response.body?.name)
        assertEquals("aabbccddeeff", response.body?.macAddress)
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

        val deviceApi = KtorDeviceApi("http://192.168.1.50/", httpClient)
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

        val deviceApi = KtorDeviceApi("http://192.168.1.50/", httpClient)
        val response = deviceApi.postJson(JsonPost(isOn = true, brightness = 128))

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertNotNull(response.body)
        assertEquals(true, response.body?.isOn)
        assertEquals(128, response.body?.brightness)
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

        val deviceApi = KtorDeviceApi("http://192.168.1.50/", httpClient)
        val response = deviceApi.updateDevice(tempFile)

        assertTrue(response.isSuccessful)
        assertEquals(200, response.code)
        assertEquals("Update Success! Rebooting...", response.body)
    }

    @Test
    fun `DeviceApiFactory creates device API with normalized url`() {
        val factory = DeviceApiFactory(OkHttpClient(), testJson)
        val device = Device(macAddress = "AABBCCDDEEFF", address = "192.168.1.100")

        val apiFromAddress = factory.create("192.168.1.100")
        assertNotNull(apiFromAddress)

        val apiFromDevice = factory.create(device)
        assertNotNull(apiFromDevice)

        val apiWithTimeout = factory.create(device, 60L)
        assertNotNull(apiWithTimeout)
    }
}
