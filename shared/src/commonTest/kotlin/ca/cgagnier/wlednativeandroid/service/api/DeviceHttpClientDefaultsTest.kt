package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceHttpClientDefaultsTest {

    private val engine = respondingWith(SAMPLE_INFO_JSON)
    private val client = HttpClient(engine.engine) { installDeviceHttpClientDefaults() }

    @Test
    fun decodesJsonResponsesIgnoringUnknownKeys() = runTest {
        val json = SAMPLE_INFO_JSON.replaceFirst("{", """{"unknown": true,""")
        val client = HttpClient(respondingWith(json).engine) { installDeviceHttpClientDefaults() }

        val info = client.get("http://10.0.0.100/json/info").body<Info>()

        assertEquals("WLED Office", info.name)
    }

    @Test
    fun encodesJsonRequestBodies() = runTest {
        client.post("http://10.0.0.100/json/state") {
            contentType(ContentType.Application.Json)
            setBody(JsonPost(brightness = 10))
        }

        assertEquals("""{"bri":10,"v":true}""", engine.lastRequest.body.toByteArray().decodeToString())
    }
}
