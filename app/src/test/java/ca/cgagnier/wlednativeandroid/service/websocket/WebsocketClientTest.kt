package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class WebsocketClientTest {

    private val httpClient: HttpClient = mockk(relaxed = true)
    private lateinit var json: Json
    private lateinit var device: Device
    private lateinit var testScope: TestScope
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        mockkStatic("io.ktor.client.plugins.websocket.BuildersKt")
        json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
            coerceInputValues = true
        }

        device = Device(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            originalName = "Living Room WLED",
        )

        testScope = TestScope(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
        unmockkAll()
    }

    // -------------------------------------------------------------------------
    // 1. Backoff Calculation Mathematics
    // -------------------------------------------------------------------------

    @Test
    fun `calculateBackoffWithJitter respects strict mathematical bounds for attempts 0 to 4`() {
        val baseDelay = 2000L
        val minRandom = deterministicRandom(0.75)
        val maxRandom = deterministicRandom(1.249999999)

        for (attempt in 0..4) {
            val powerOfTwo = 1L shl attempt
            val expectedMin = (baseDelay * powerOfTwo * 0.75).toLong()
            val expectedMax = (baseDelay * powerOfTwo * 1.25).toLong()

            val minResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = minRandom)
            val maxResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = maxRandom)

            assertEquals(expectedMin, minResult, "Attempt $attempt min bound violated")
            assertTrue(
                maxResult in expectedMin..expectedMax,
                "Attempt $attempt max bound violated: got $maxResult, expected <= $expectedMax",
            )
        }
    }

    @Test
    fun `calculateBackoffWithJitter is strictly capped at 60000ms for attempts 5 and higher`() {
        val minRandom = deterministicRandom(0.75)
        val maxRandom = deterministicRandom(1.25)
        val neutralRandom = deterministicRandom(1.0)

        val testAttempts = listOf(5, 6, 7, 10, 20, 30, 31, 50, 100, 1000, Int.MAX_VALUE)

        for (attempt in testAttempts) {
            val minResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = minRandom)
            val neutralResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = neutralRandom)
            val maxResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = maxRandom)

            assertEquals(45000L, minResult, "Attempt $attempt min at cap should be 45000")
            assertEquals(60000L, neutralResult, "Attempt $attempt neutral at cap should be 60000")
            assertEquals(60000L, maxResult, "Attempt $attempt max at cap should be capped at 60000")
        }
    }

    @Test
    fun `calculateBackoffWithJitter throws IllegalArgumentException for negative attempt`() {
        assertThrows(IllegalArgumentException::class.java) {
            WebsocketClient.calculateBackoffWithJitter(-1)
        }
    }

    // -------------------------------------------------------------------------
    // 2. Protocol Compliance: URL Formatting and Constants
    // -------------------------------------------------------------------------

    @Test
    fun `buildWebsocketUrl correctly formats diverse host and IP address inputs`() {
        val client = WebsocketClient(device, httpClient, json)

        val testCases = mapOf(
            "192.168.1.100" to "ws://192.168.1.100/ws",
            "http://192.168.1.100" to "ws://192.168.1.100/ws",
            "https://192.168.1.100" to "ws://192.168.1.100/ws",
            "ws://192.168.1.100" to "ws://192.168.1.100/ws",
            "wss://192.168.1.100" to "ws://192.168.1.100/ws",
            "192.168.1.100/" to "ws://192.168.1.100/ws",
            "http://192.168.1.100/" to "ws://192.168.1.100/ws",
            "192.168.1.100///" to "ws://192.168.1.100/ws",
            "192.168.1.100:8080" to "ws://192.168.1.100:8080/ws",
            "http://192.168.1.100:8080/" to "ws://192.168.1.100:8080/ws",
            "wled-device.local" to "ws://wled-device.local/ws",
            "http://wled-device.local" to "ws://wled-device.local/ws",
            "[fe80::1]" to "ws://[fe80::1]/ws",
            "http://[fe80::1]:80/" to "ws://[fe80::1]:80/ws",
        )

        for ((input, expected) in testCases) {
            val result = client.buildWebsocketUrl(input)
            assertEquals(expected, result, "URL format mismatch for input '$input'")
        }
    }

    @Test
    fun `userAgent constant complies with WLED requirements`() {
        assertEquals("WLED-Android", WebsocketClient.USER_AGENT, "User-Agent must be exactly 'WLED-Android'")
    }

    // -------------------------------------------------------------------------
    // 3. Frame Decoding Resilience
    // -------------------------------------------------------------------------

    @Test
    fun `handleTextFrame successfully parses valid DeviceStateInfo JSON and updates state`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        var emittedStateInfo: ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo? = null
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.incomingStateInfo.collect { emittedStateInfo = it }
        }

        client.handleTextFrame(VALID_DEVICE_STATE_INFO_JSON)
        testScheduler.runCurrent()

        val stateInfo = client.stateInfo.value
        assertNotNull(stateInfo, "stateInfo should be populated")
        assertEquals("WLED Desk", stateInfo?.info?.name)
        assertEquals("16.0.1", stateInfo?.info?.version)
        assertEquals(true, stateInfo?.state?.isOn)
        assertEquals(195, stateInfo?.state?.brightness)
        assertEquals("aabbccddeeff", stateInfo?.info?.macAddress)
        assertEquals("wled/WLED", stateInfo?.info?.repository)
        assertEquals("WLED", stateInfo?.info?.brand)
        assertEquals(277, stateInfo?.info?.leds?.count)

        assertEquals(stateInfo, emittedStateInfo, "incomingStateInfo flow should emit parsed model")
        collectJob.cancel()
    }

    @Test
    fun `handleTextFrame handles malformed, partial, and garbage JSON without throwing`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        val malformedPayloads = listOf(
            "",
            "   ",
            "not a json",
            "{\"state\":",
            "{\"info\": null}",
            "<html><body>404 Not Found</body></html>",
            "{\"state\": {\"bri\": \"not_a_number\"}}",
            "\u0000\u0001\u0002BINARY_DATA",
        )

        for (garbage in malformedPayloads) {
            client.handleTextFrame(garbage)
            assertNull(client.stateInfo.value, "State must remain null after malformed frame")
        }
    }

    @Test
    fun `handleTextFrame survives extra unknown fields with ignoreUnknownKeys`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        client.handleTextFrame(UNKNOWN_FIELDS_DEVICE_STATE_INFO_JSON)

        val parsed = client.stateInfo.value
        assertNotNull(parsed, "Parsed object must not be null when unknown fields are present")
        assertEquals("Forward-Compatible WLED", parsed?.info?.name)
        assertEquals("0.15.0", parsed?.info?.version)
        assertEquals(true, parsed?.state?.isOn)
        assertEquals(255, parsed?.state?.brightness)
    }

    // -------------------------------------------------------------------------
    // 4. Lifecycle and Concurrency
    // -------------------------------------------------------------------------

    @Test
    fun `sendState returns false when disconnected without throwing`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        val state = State(isOn = true, brightness = 100)
        val result = client.sendState(state)

        assertFalse(result, "sendState must return false when disconnected")
    }

    @Test
    fun `disconnect when already disconnected is safe and idempotent`() {
        val client = WebsocketClient(device, httpClient, json)

        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
        client.disconnect()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
        client.disconnect()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
    }

    @Test
    fun `retryCount resets to 0 after successful connection drops`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val session1Incoming = Channel<Frame>(Channel.UNLIMITED)
        val session1Outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session1 = mockk<DefaultClientWebSocketSession>(relaxed = true)
        every { session1.incoming } returns session1Incoming
        every { session1.outgoing } returns session1Outgoing
        every { session1.coroutineContext } returns
            SupervisorJob() + Dispatchers.Default

        val connectionAttempts = AtomicInteger(0)

        coEvery { httpClient.webSocketSession(any<String>(), any()) } coAnswers {
            val attempt = connectionAttempts.incrementAndGet()
            when (attempt) {
                1 -> session1
                else -> throw IOException("Subsequent reconnect failed")
            }
        }

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
        )

        client.connect()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.CONNECTED, client.status.value)
        assertEquals(1, connectionAttempts.get())

        // Drop session 1
        session1Incoming.close()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Advance 2100ms for backoff attempt 0 (base delay 2000ms +- 25% = 1500-2500ms)
        localScope.advanceTimeBy(2600L)
        localScope.runCurrent()

        assertEquals(2, connectionAttempts.get())

        client.destroy()
    }

    @Test
    fun `calling connect while sleeping in backoff delay interrupts sleep and reconnects immediately`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val session1Incoming = Channel<Frame>(Channel.UNLIMITED)
        val session1Outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session1 = mockk<DefaultClientWebSocketSession>(relaxed = true)
        every { session1.incoming } returns session1Incoming
        every { session1.outgoing } returns session1Outgoing
        every { session1.coroutineContext } returns SupervisorJob() + Dispatchers.Default

        val session2Incoming = Channel<Frame>(Channel.UNLIMITED)
        val session2Outgoing = Channel<Frame>(Channel.UNLIMITED)
        val session2 = mockk<DefaultClientWebSocketSession>(relaxed = true)
        every { session2.incoming } returns session2Incoming
        every { session2.outgoing } returns session2Outgoing
        every { session2.coroutineContext } returns SupervisorJob() + Dispatchers.Default

        val connectionAttempts = AtomicInteger(0)

        coEvery { httpClient.webSocketSession(any<String>(), any()) } coAnswers {
            val attempt = connectionAttempts.incrementAndGet()
            when (attempt) {
                1 -> session1
                else -> session2
            }
        }

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
        )

        client.connect()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.CONNECTED, client.status.value)
        assertEquals(1, connectionAttempts.get())

        // Drop session 1 -> triggers reconnection loop with backoff delay
        session1Incoming.close()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Advance only 100ms (far before the 1500-2500ms backoff window)
        localScope.advanceTimeBy(100L)
        localScope.runCurrent()
        assertEquals(1, connectionAttempts.get(), "Must still be sleeping in backoff delay")

        // Manual connect during backoff sleep must cancel the sleep and connect immediately
        client.connect()
        localScope.runCurrent()

        assertEquals(2, connectionAttempts.get(), "Immediate reconnection must occur on manual connect")
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        client.destroy()
    }

    @Test
    fun `destroy shuts down client cleanly`() {
        val client = WebsocketClient(device, httpClient, json)
        client.destroy()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
    }

    private fun deterministicRandom(fixedValue: Double): Random = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(from: Double, until: Double): Double =
            (from + (until - from) * ((fixedValue - 0.75) / 0.5)).coerceIn(from, until)
    }
}

private val VALID_DEVICE_STATE_INFO_JSON = """
    {
      "state": {
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
        "seg": []
      },
      "info": {
        "ver": "16.0.1",
        "vid": 2606300,
        "cn": "Niji",
        "release": "ESP32",
        "repo": "wled/WLED",
        "name": "WLED Desk",
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
        "freeheap": 120932,
        "uptime": 2252733,
        "time": "2026-9-22, 23:30:48",
        "opt": 79,
        "brand": "WLED",
        "product": "FOSS",
        "mac": "aabbccddeeff",
        "ip": "192.168.1.100",
        "leds": {
          "count": 277,
          "pwr": 2171,
          "fps": 43,
          "maxpwr": 10002,
          "maxseg": 32,
          "actseg": 1,
          "seglc": [277],
          "lc": 1,
          "rgbw": false,
          "wv": 0,
          "cct": 0
        },
        "wifi": {
          "bssid": "aa:bb:cc:dd:ee:ff",
          "rssi": -65,
          "signal": 70,
          "channel": 1,
          "ap": false
        },
        "str": false
      }
    }
""".trimIndent()

private val UNKNOWN_FIELDS_DEVICE_STATE_INFO_JSON = """
    {
      "unknown_root_property": "ignored_value",
      "experimental_array": [1, 2, 3],
      "state": {
        "on": true,
        "bri": 255,
        "future_feature_flag": true,
        "nested_unknown_object": {
          "sub_key": 42
        }
      },
      "info": {
        "name": "Forward-Compatible WLED",
        "ver": "0.15.0",
        "brand_new_telemetry": "active",
        "leds": {
          "count": 60,
          "hardware_revision": "v3"
        },
        "wifi": {
          "rssi": -55
        }
      }
    }
""".trimIndent()
