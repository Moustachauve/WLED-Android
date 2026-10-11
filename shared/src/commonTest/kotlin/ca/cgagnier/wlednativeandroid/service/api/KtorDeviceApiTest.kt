package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.serialization.JsonConvertException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KtorDeviceApiTest {

    private val baseUrl = "http://10.0.0.100/"

    private fun RecordingEngine.api(base: String = baseUrl) = KtorDeviceApi(base, client())

    private suspend fun OutgoingContent.text(): String = toByteArray().decodeToString()

    // region getInfo

    @Test
    fun getInfoSendsGetToJsonInfo() = runTest {
        val engine = respondingWith(SAMPLE_INFO_JSON)

        engine.api().getInfo()

        assertEquals(HttpMethod.Get, engine.lastRequest.method)
        assertEquals("http://10.0.0.100/json/info", engine.lastRequest.url.toString())
    }

    @Test
    fun getInfoAddsSlashWhenBaseUrlHasNone() = runTest {
        val engine = respondingWith(SAMPLE_INFO_JSON)

        engine.api(base = "http://10.0.0.100").getInfo()

        assertEquals("http://10.0.0.100/json/info", engine.lastRequest.url.toString())
    }

    @Test
    fun getInfoDecodesBodyOnSuccess() = runTest {
        val response = respondingWith(SAMPLE_INFO_JSON).api().getInfo()

        assertTrue(response.isSuccessful)
        assertEquals("WLED Office", response.body?.name)
        assertEquals("001122334455", response.body?.macAddress)
        assertEquals("16.0.1", response.body?.version)
        assertEquals(277, response.body?.leds?.count)
        assertNull(response.errorBody)
    }

    @Test
    fun getInfoIgnoresUnknownFields() = runTest {
        val json = SAMPLE_INFO_JSON.replaceFirst("{", """{"someFutureField": {"nested": [1, 2]},""")

        val response = respondingWith(json).api().getInfo()

        assertEquals("WLED Office", response.body?.name)
    }

    @Test
    fun getInfoReturnsErrorBodyOnHttpError() = runTest {
        val engine = respondingWith("Not Found", HttpStatusCode.NotFound, ContentType.Text.Plain)

        val response = engine.api().getInfo()

        assertEquals(404, response.code)
        assertNull(response.body)
        assertEquals("Not Found", response.errorBody)
    }

    @Test
    fun getInfoThrowsOnMalformedJson() = runTest {
        val api = respondingWith("{ not json").api()

        assertFailsWith<JsonConvertException> { api.getInfo() }
    }

    @Test
    fun getInfoPropagatesTransportFailure() = runTest {
        val api = RecordingEngine { throw FakeTransportException() }.api()

        assertFailsWith<FakeTransportException> { api.getInfo() }
    }

    // endregion

    // region postJson

    @Test
    fun postJsonSendsPostToJsonState() = runTest {
        val engine = respondingWith(SAMPLE_STATE_JSON)

        engine.api().postJson(JsonPost(isOn = true))

        assertEquals(HttpMethod.Post, engine.lastRequest.method)
        assertEquals("http://10.0.0.100/json/state", engine.lastRequest.url.toString())
    }

    @Test
    fun postJsonSendsJsonEncodedBody() = runTest {
        val engine = respondingWith(SAMPLE_STATE_JSON)

        engine.api().postJson(JsonPost(isOn = true, brightness = 195))

        val body = engine.lastRequest.body
        assertEquals(ContentType.Application.Json, body.contentType?.withoutParameters())
        assertEquals("""{"on":true,"bri":195,"v":true}""", body.text())
    }

    @Test
    fun postJsonDecodesStateOnSuccess() = runTest {
        val response = respondingWith(SAMPLE_STATE_JSON).api().postJson(JsonPost(isOn = true))

        assertTrue(response.isSuccessful)
        assertEquals(true, response.body?.isOn)
        assertEquals(195, response.body?.brightness)
    }

    @Test
    fun postJsonReturnsErrorBodyOnHttpError() = runTest {
        val engine = respondingWith("Bad Request", HttpStatusCode.BadRequest, ContentType.Text.Plain)

        val response = engine.api().postJson(JsonPost(isOn = true))

        assertEquals(400, response.code)
        assertNull(response.body)
        assertEquals("Bad Request", response.errorBody)
    }

    @Test
    fun postJsonPropagatesTransportFailure() = runTest {
        val api = RecordingEngine { throw FakeTransportException() }.api()

        assertFailsWith<FakeTransportException> { api.postJson(JsonPost(isOn = true)) }
    }

    // endregion

    // region updateDevice

    private val firmware = byteArrayOf(0x01, 0x02, 0x03, 0x04)

    @Test
    fun updateDeviceSendsMultipartPostToUpdate() = runTest {
        val engine = respondingWith("OK", contentType = ContentType.Text.Html)

        engine.api().updateDevice(firmware, "wled.bin")

        assertEquals(HttpMethod.Post, engine.lastRequest.method)
        assertEquals("http://10.0.0.100/update", engine.lastRequest.url.toString())
        assertEquals(ContentType.MultiPart.FormData, engine.lastRequest.body.contentType?.withoutParameters())
    }

    @Test
    fun updateDeviceSendsFileNameInContentDisposition() = runTest {
        val engine = respondingWith("OK", contentType = ContentType.Text.Html)

        engine.api().updateDevice(firmware, "wled.bin")

        assertTrue(engine.lastRequest.body.text().contains("filename=\"wled.bin\""))
    }

    @Test
    fun updateDeviceReturnsTextBodyOnSuccess() = runTest {
        val engine = respondingWith("Update Success! Rebooting...", contentType = ContentType.Text.Html)

        val response = engine.api().updateDevice(firmware, "wled.bin")

        assertTrue(response.isSuccessful)
        assertEquals("Update Success! Rebooting...", response.body)
        assertNull(response.errorBody)
    }

    @Test
    fun updateDeviceReturnsErrorBodyOnHttpError() = runTest {
        val engine = respondingWith(
            "Update Failed: Not enough space",
            HttpStatusCode.InternalServerError,
            ContentType.Text.Plain,
        )

        val response = engine.api().updateDevice(firmware, "wled.bin")

        assertEquals(500, response.code)
        assertNull(response.body)
        assertEquals("Update Failed: Not enough space", response.errorBody)
    }

    @Test
    fun updateDevicePropagatesTransportFailure() = runTest {
        val api = RecordingEngine { throw FakeTransportException() }.api()

        assertFailsWith<FakeTransportException> { api.updateDevice(firmware, "wled.bin") }
    }

    // endregion
}
