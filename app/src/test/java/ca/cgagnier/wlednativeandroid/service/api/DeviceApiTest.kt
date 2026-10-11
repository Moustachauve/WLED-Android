package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for the Android-only [DeviceApi] extensions.
 *
 * Platform-independent behavior of [KtorDeviceApi] and [DeviceApiFactory] is covered in
 * `shared/src/commonTest` so that it runs on every target.
 */
class DeviceApiTest {

    private val requests = mutableListOf<HttpRequestData>()
    private val httpClient = HttpClient(
        MockEngine { request ->
            requests += request
            respond(
                content = "Update Success! Rebooting...",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        },
    )

    private fun firmwareFile(): File = File.createTempFile("wled_test_update", ".bin").apply {
        writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
        deleteOnExit()
    }

    @Test
    fun `updateDevice with File posts to update endpoint`() = runTest {
        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)

        val response = deviceApi.updateDevice(firmwareFile())

        assertEquals("/update", requests.single().url.encodedPath)
        assertEquals("Update Success! Rebooting...", response.body)
    }

    @Test
    fun `updateDevice with File uses the file name`() = runTest {
        val file = firmwareFile()
        val deviceApi = KtorDeviceApi("http://10.0.0.100/", httpClient)

        deviceApi.updateDevice(file)

        val body = requests.single().body.toByteArray().decodeToString()
        assertTrue(body.contains("filename=\"${file.name}\""))
    }

    @Test
    fun `DeviceApiFactory can be built from an OkHttpClient`() {
        val factory = DeviceApiFactory(OkHttpClient())

        assertNotNull(factory.create("10.0.0.100"))
    }
}
