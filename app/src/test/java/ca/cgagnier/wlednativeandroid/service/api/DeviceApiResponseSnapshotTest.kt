package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.test.TestJson
import com.diffplug.selfie.Selfie.expectSelfie
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Snapshot tests decoding real WLED responses through [KtorDeviceApi] with the production client
 * configuration, so any change in how a field is decoded shows up in the snapshot diff.
 *
 * Samples live in `src/test/resources/wledapi` (real device output, anonymized IP/MAC/BSSID).
 */
class DeviceApiResponseSnapshotTest {

    private fun apiResponding(resource: String): DeviceApi {
        val body = checkNotNull(javaClass.getResource("/wledapi/$resource")) { "Missing $resource" }.readText()
        val client = HttpClient(
            MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) },
        ) { install(ContentNegotiation) { json(defaultJson) } }
        return KtorDeviceApi("http://10.0.0.100/", client)
    }

    @Test
    fun `getInfo decodes WLED 16_0_1 response`() = runTest {
        val info = apiResponding("info_16.0.1.json").getInfo().body

        expectSelfie(TestJson.api.encodeToString(info)).toMatchDisk()
    }

    @Test
    fun `postJson decodes WLED 16_0_1 state response`() = runTest {
        val state = apiResponding("state_16.0.1.json").postJson(JsonPost(isOn = true)).body

        expectSelfie(TestJson.api.encodeToString(state)).toMatchDisk()
    }
}
