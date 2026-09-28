package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
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
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
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
        val minRandom = deterministicRandom(0.0)
        val maxRandom = deterministicRandom(1.0)

        for (attempt in 0..4) {
            val powerOfTwo = 1L shl attempt
            val expectedMin = (baseDelay * powerOfTwo * 0.75).toLong()
            val expectedMax = (baseDelay * powerOfTwo * 1.25).toLong()

            val minResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = minRandom)
            val maxResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = maxRandom)

            assertEquals(expectedMin, minResult, "Attempt $attempt min bound violated")
            assertEquals(expectedMax, maxResult, "Attempt $attempt max bound violated")
        }
    }

    @Test
    fun `calculateBackoffWithJitter is strictly capped at 60000ms for attempts 5 and higher`() {
        val minRandom = deterministicRandom(0.0)
        val neutralRandom = deterministicRandom(0.5)
        val maxRandom = deterministicRandom(1.0)

        val testAttempts = listOf(5, 6, 7, 10, 20, 30, 31, 50, 100, 1000, Int.MAX_VALUE)

        for (attempt in testAttempts) {
            val minResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = minRandom)
            val neutralResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = neutralRandom)
            val maxResult = WebsocketClient.calculateBackoffWithJitter(attempt, random = maxRandom)

            assertEquals(45000L, minResult, "Attempt $attempt min at cap should be 45000")
            assertEquals(52500L, neutralResult, "Attempt $attempt neutral at cap should be 52500")
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

    @ParameterizedTest
    @CsvSource(
        "192.168.1.100, ws://192.168.1.100/ws",
        "http://192.168.1.100, ws://192.168.1.100/ws",
        "https://192.168.1.100, wss://192.168.1.100/ws",
        "ws://192.168.1.100, ws://192.168.1.100/ws",
        "wss://192.168.1.100, wss://192.168.1.100/ws",
        "192.168.1.100/, ws://192.168.1.100/ws",
        "http://192.168.1.100/, ws://192.168.1.100/ws",
        "https://192.168.1.100/, wss://192.168.1.100/ws",
        "192.168.1.100///, ws://192.168.1.100/ws",
        "192.168.1.100:8080, ws://192.168.1.100:8080/ws",
        "http://192.168.1.100:8080/, ws://192.168.1.100:8080/ws",
        "https://192.168.1.100:8443/, wss://192.168.1.100:8443/ws",
        "wled-device.local, ws://wled-device.local/ws",
        "http://wled-device.local, ws://wled-device.local/ws",
        "https://wled-device.local, wss://wled-device.local/ws",
        "192.168.1.100/ws, ws://192.168.1.100/ws",
        "http://192.168.1.100/ws/, ws://192.168.1.100/ws",
        "https://192.168.1.100/ws, wss://192.168.1.100/ws",
        "wss://192.168.1.100/ws/, wss://192.168.1.100/ws",
        "[fe80::1], ws://[fe80::1]/ws",
        "http://[fe80::1]:80/, ws://[fe80::1]:80/ws",
        "https://[fe80::1]:443/, wss://[fe80::1]:443/ws",
    )
    fun `buildWebsocketUrl correctly formats diverse host and IP address inputs`(input: String, expected: String) {
        val client = WebsocketClient(device, httpClient, json)
        val result = client.buildWebsocketUrl(input)
        assertEquals(expected, result)
    }

    @Test
    fun `userAgent constant complies with WLED requirements`() {
        assertEquals("WLED-Android", WebsocketClient.USER_AGENT, "User-Agent must be exactly 'WLED-Android'")
    }

    // -------------------------------------------------------------------------
    // 3. Frame Decoding Resilience
    // -------------------------------------------------------------------------

    @Test
    fun `handleTextFrame successfully parses valid DeviceStateInfo JSON and emits to incomingStateInfo`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        var emittedStateInfo: ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo? = null
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.incomingStateInfo.collect { emittedStateInfo = it }
        }

        client.handleTextFrame(VALID_DEVICE_STATE_INFO_JSON)
        testScheduler.runCurrent()

        assertNotNull(emittedStateInfo, "incomingStateInfo should emit parsed model")
        assertEquals("WLED Desk", emittedStateInfo?.info?.name)
        collectJob.cancel()
    }

    @Test
    fun `handleTextFrame handles malformed, partial, and garbage JSON without throwing`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        var emittedCount = 0
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            client.incomingStateInfo.collect { emittedCount++ }
        }

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
        }
        testScheduler.runCurrent()
        assertEquals(0, emittedCount, "No state should be emitted for malformed frames")
        collectJob.cancel()
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

    private class MockSessionHolder(
        val session: DefaultClientWebSocketSession,
        val incoming: Channel<Frame>,
        val outgoing: Channel<Frame>,
        val job: CompletableJob,
    )

    private fun createMockSession(relaxed: Boolean = false): MockSessionHolder {
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        val sessionJob = SupervisorJob()
        val session = mockk<DefaultClientWebSocketSession>(relaxed = relaxed)
        every { session.incoming } returns incoming
        every { session.outgoing } returns outgoing
        every { session.coroutineContext } returns sessionJob + Dispatchers.Default
        coEvery { session.send(any()) } coAnswers { outgoing.send(firstArg()) }
        return MockSessionHolder(session, incoming, outgoing, sessionJob)
    }

    @Test
    fun `retryCount resets to 0 after successful connection drops`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val session1 = createMockSession(relaxed = true)
        val connectionAttempts = AtomicInteger(0)

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ ->
                val attempt = connectionAttempts.incrementAndGet()
                when (attempt) {
                    1 -> session1.session
                    else -> throw IOException("Subsequent reconnect failed")
                }
            },
        )

        client.connect()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.CONNECTED, client.status.value)
        assertEquals(1, connectionAttempts.get())

        // Drop session 1
        session1.incoming.close()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Advance 2600ms for backoff attempt 0 (base delay 2000ms +- 25% = 1500-2500ms)
        localScope.advanceTimeBy(2600L)
        localScope.runCurrent()

        assertEquals(2, connectionAttempts.get())

        client.destroy()
    }

    @Test
    fun `calling connect while sleeping in backoff delay interrupts sleep and reconnects immediately`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val session1 = createMockSession(relaxed = true)
        val session2 = createMockSession(relaxed = true)
        val connectionAttempts = AtomicInteger(0)

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ ->
                val attempt = connectionAttempts.incrementAndGet()
                when (attempt) {
                    1 -> session1.session
                    else -> session2.session
                }
            },
        )

        client.connect()
        localScope.runCurrent()

        assertEquals(WebsocketStatus.CONNECTED, client.status.value)
        assertEquals(1, connectionAttempts.get())

        // Drop session 1 -> triggers reconnection loop with backoff delay
        session1.incoming.close()
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

    @Test
    fun `calling connect on destroyed client does not connect`() = runTest {
        val client = WebsocketClient(device, httpClient, json)
        client.destroy()
        client.connect()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
    }

    @Test
    fun `sendState returns false when client is destroyed`() = runTest {
        val client = WebsocketClient(device, httpClient, json)
        client.destroy()
        val result = client.sendState(State(isOn = true))
        assertFalse(result)
    }

    @Test
    fun `sendState sends JSON text frame over active session`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val sessionHolder = createMockSession()

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ -> sessionHolder.session },
        )

        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        val sendResult = client.sendState(State(isOn = true, brightness = 128))
        assertTrue(sendResult)

        val sentFrame = sessionHolder.outgoing.receive()
        assertTrue(sentFrame is Frame.Text)
        val sentText = (sentFrame as Frame.Text).readText()
        assertTrue(sentText.contains("\"bri\":128"))
        assertTrue(sentText.contains("\"on\":true"))

        sessionHolder.incoming.close()
        client.destroy()
        localScope.runCurrent()
    }

    @Test
    fun `sendState returns false when session channel is closed without cancelling caller coroutine`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val sessionHolder = createMockSession()
        coEvery { sessionHolder.session.send(any()) } throws CancellationException("Channel was closed")

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ -> sessionHolder.session },
        )

        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        val sendResult = client.sendState(State(isOn = true))
        assertFalse(sendResult)
        assertTrue(coroutineContext.isActive, "Caller coroutine must remain active after sendState failure")

        sessionHolder.incoming.close()
        client.destroy()
        localScope.runCurrent()
    }

    @Test
    fun `disconnect keeps manual disconnect sticky across resume`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val sessionHolder = createMockSession()

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ -> sessionHolder.session },
        )

        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        // Explicit manual disconnect
        client.disconnect()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Lifecycle resume must NOT re-open a manually disconnected client
        client.resume()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Explicit connect() overrides manual disconnect and reconnects
        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        sessionHolder.incoming.close()
        client.destroy()
        localScope.runCurrent()
    }

    @Test
    fun `pause followed by resume reconnects without manual disconnect`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val sessionHolder = createMockSession()

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ -> sessionHolder.session },
        )

        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        // App backgrounded: pause()
        client.pause()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // App foregrounded: resume() successfully reconnects
        client.resume()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)

        sessionHolder.incoming.close()
        client.destroy()
        localScope.runCurrent()
    }

    @Test
    fun `sendState returns false and does not reconnect when paused`() = runTest {
        val client = WebsocketClient(device, httpClient, json)
        client.pause()
        val result = client.sendState(State(isOn = true))
        assertFalse(result, "sendState must return false when paused")
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)
    }

    @Test
    fun `disconnect synchronously cancels underlying session`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val sessionHolder = createMockSession()

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            sessionOpener = { _, _ -> sessionHolder.session },
        )

        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)
        assertTrue(sessionHolder.job.isActive)

        // Disconnecting must cancel the session job synchronously
        client.disconnect()
        assertTrue(sessionHolder.job.isCancelled, "Session job must be cancelled immediately on disconnect")
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        sessionHolder.incoming.close()
        client.destroy()
        localScope.runCurrent()
    }

    private fun deterministicRandom(fixedValue: Double): Random = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(from: Double, until: Double): Double = fixedValue.coerceIn(from, until)
    }
}

private val VALID_DEVICE_STATE_INFO_JSON = """
    {
      "state": {
        "on": true,
        "bri": 195,
        "transition": 7
      },
      "info": {
        "ver": "16.0.1",
        "name": "WLED Desk",
        "leds": { "count": 60 },
        "wifi": { "bssid": "aa:bb:cc:dd:ee:ff", "rssi": -65, "signal": 70, "channel": 1 }
      }
    }
""".trimIndent()
