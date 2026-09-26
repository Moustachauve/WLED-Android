package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WebsocketClientFactoryTest {

    private val httpClient: HttpClient = mockk(relaxed = true)
    private lateinit var json: Json
    private lateinit var factory: WebsocketClientFactory

    @BeforeEach
    fun setUp() {
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }

        factory = WebsocketClientFactory(
            httpClient = httpClient,
            json = json,
        )
    }

    @Test
    fun `create returns WebsocketClient with correct device`() {
        val device = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Test Device")

        val client = factory.create(device)

        assertNotNull(client)
        assertEquals(device, client.device)
    }

    @Test
    fun `create returns different instances for different devices`() {
        val device1 = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Device 1")
        val device2 = createTestDevice("112233445566", "192.168.1.101", "Device 2")

        val client1 = factory.create(device1)
        val client2 = factory.create(device2)

        assertNotSame(client1, client2)
        assertEquals("AABBCCDDEEFF", client1.device.macAddress)
        assertEquals("112233445566", client2.device.macAddress)
    }

    @Test
    fun `create returns client with initial disconnected status`() {
        val device = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Test")

        val client = factory.create(device)

        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
    }

    private fun createTestDevice(macAddress: String, address: String, originalName: String): Device = Device(
        macAddress = macAddress,
        address = address,
        originalName = originalName,
    )
}
