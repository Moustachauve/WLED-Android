package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DeviceApiFactoryTest {

    private val engine = respondingWith(SAMPLE_INFO_JSON)
    private val factory = DeviceApiFactory(engine.client())

    private suspend fun requestedUrlFor(api: DeviceApi): String {
        api.getInfo()
        return engine.lastRequest.url.toString()
    }

    @Test
    fun createFromBareAddressUsesHttp() = runTest {
        assertEquals("http://10.0.0.100/json/info", requestedUrlFor(factory.create("10.0.0.100")))
    }

    @Test
    fun createFromHostnameUsesHttp() = runTest {
        assertEquals("http://wled.local/json/info", requestedUrlFor(factory.create("wled.local")))
    }

    @Test
    fun createKeepsExplicitHttpsScheme() = runTest {
        assertEquals("https://wled.example.com/json/info", requestedUrlFor(factory.create("https://wled.example.com")))
    }

    @Test
    fun createKeepsExplicitHttpSchemeWithTrailingSlash() = runTest {
        assertEquals("http://10.0.0.100/json/info", requestedUrlFor(factory.create("http://10.0.0.100/")))
    }

    @Test
    fun createFromDeviceUsesDeviceAddress() = runTest {
        val device = Device(macAddress = "AABBCCDDEEFF", address = "10.0.0.42")

        assertEquals("http://10.0.0.42/json/info", requestedUrlFor(factory.create(device)))
    }

    @Test
    fun createWithTimeoutUsesDeviceAddress() = runTest {
        val device = Device(macAddress = "AABBCCDDEEFF", address = "10.0.0.42")

        assertEquals("http://10.0.0.42/json/info", requestedUrlFor(factory.create(device, timeout = 5)))
    }

    @Test
    fun createWithTimeoutFailsWhenDeviceIsSlowerThanTimeout() = runTest {
        val slowEngine = RecordingEngine {
            delay(SLOW_RESPONSE_MILLIS)
            respond(SAMPLE_INFO_JSON)
        }
        val device = Device(macAddress = "AABBCCDDEEFF", address = "10.0.0.42")
        val api = DeviceApiFactory(slowEngine.client()).create(device, timeout = 1)

        assertFailsWith<HttpRequestTimeoutException> { api.getInfo() }
    }

    private companion object {
        const val SLOW_RESPONSE_MILLIS = 60_000L
    }
}
