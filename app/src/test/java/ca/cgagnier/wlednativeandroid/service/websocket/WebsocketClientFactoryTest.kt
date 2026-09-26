package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.HttpClient
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

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
        assertEquals("AABBCCDDEEFF", client.device.macAddress)
        assertEquals("192.168.1.100", client.device.address)
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
    fun `create preserves device properties`() {
        val device = createTestDevice(
            macAddress = "FFEEDDCCBBAA",
            address = "10.0.0.50",
            originalName = "Kitchen Lights",
        )

        val client = factory.create(device)

        assertEquals("FFEEDDCCBBAA", client.device.macAddress)
        assertEquals("10.0.0.50", client.device.address)
        assertEquals("Kitchen Lights", client.device.originalName)
    }

    @Test
    fun `create returns client with initial disconnected status`() {
        val device = createTestDevice("AABBCCDDEEFF", "192.168.1.100", "Test")

        val client = factory.create(device)

        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
    }

    @Test
    fun `calculateBackoffWithJitter respects bounds for attempt 0`() {
        // Base 2000ms with +-25% jitter -> range [1500, 2500]
        val minRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(from: Double, until: Double): Double = from
        }
        val maxRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextDouble(from: Double, until: Double): Double = until
        }

        val minDelay = WebsocketClient.calculateBackoffWithJitter(0, random = minRandom)
        val maxDelay = WebsocketClient.calculateBackoffWithJitter(0, random = maxRandom)

        assertEquals(1500L, minDelay)
        assertEquals(2500L, maxDelay)
    }

    @Test
    fun `calculateBackoffWithJitter is capped at maxDelay for large retry count`() {
        val random = Random(42)
        val delayAttempt10 = WebsocketClient.calculateBackoffWithJitter(10, random = random)
        val delayAttempt50 = WebsocketClient.calculateBackoffWithJitter(50, random = random)

        assertTrue(delayAttempt10 <= 60000L)
        assertTrue(delayAttempt50 <= 60000L)
    }

    private fun createTestDevice(macAddress: String, address: String, originalName: String): Device = Device(
        macAddress = macAddress,
        address = address,
        originalName = originalName,
    )
}
