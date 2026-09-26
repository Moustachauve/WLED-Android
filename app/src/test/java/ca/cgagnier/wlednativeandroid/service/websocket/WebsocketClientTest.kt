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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
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
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
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
    // 1. Backoff Calculation Mathematics and Jitter Distribution
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

            // At 60000 cap, min factor 0.75 gives 45000, max factor 1.25 clamped to 60000
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
        assertThrows(IllegalArgumentException::class.java) {
            WebsocketClient.calculateBackoffWithJitter(Int.MIN_VALUE)
        }
    }

    @Test
    fun `calculateBackoffWithJitter stress test 10000 iterations verifies bounds and no negative values`() {
        val random = Random(12345)
        for (i in 0 until 10000) {
            val attempt = random.nextInt(0, 50)
            val backoff = WebsocketClient.calculateBackoffWithJitter(attempt, random = random)

            assertTrue(backoff >= 0L, "Backoff must never be negative: $backoff")
            assertTrue(backoff <= 60000L, "Backoff must never exceed 60000: $backoff")

            if (attempt <= 4) {
                val powerOfTwo = 1L shl attempt
                val min = (2000L * powerOfTwo * 0.75).toLong()
                val max = (2000L * powerOfTwo * 1.25).toLong()
                assertTrue(
                    backoff in min..max,
                    "Attempt $attempt value $backoff out of range [$min, $max]",
                )
            } else {
                assertTrue(
                    backoff in 45000L..60000L,
                    "Attempt $attempt value $backoff out of capped range [45000, 60000]",
                )
            }
        }
    }

    @Test
    fun `calculateBackoffWithJitter exhibits uniform distribution across jitter range`() {
        val sampleSize = 10000
        val bucketCount = 10
        val minDelay = 1500L
        val maxDelay = 2500L
        val bucketWidth = (maxDelay - minDelay) / bucketCount // 100ms per bucket

        val buckets = IntArray(bucketCount)
        val rng = Random(9999)

        for (i in 0 until sampleSize) {
            val delay = WebsocketClient.calculateBackoffWithJitter(0, random = rng)
            val bucketIndex = ((delay - minDelay) / bucketWidth).toInt().coerceIn(0, bucketCount - 1)
            buckets[bucketIndex]++
        }

        // Expected count per bucket is ~1000. With 10,000 samples, every bucket should have at least 600
        for (i in 0 until bucketCount) {
            assertTrue(
                buckets[i] > 600,
                "Bucket $i had only ${buckets[i]} samples, indicating poor or skewed distribution",
            )
        }
    }

    // -------------------------------------------------------------------------
    // 2. Protocol Compliance: URL Formatting and Request Headers
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
            val result = invokeBuildWebsocketUrl(client, input)
            assertEquals(expected, result, "URL format mismatch for input '$input'")
        }
    }

    @Test
    fun `userAgent constant and request header complies with WLED requirements`() {
        val userAgentField: Field = WebsocketClient::class.java.getDeclaredField("USER_AGENT")
        userAgentField.isAccessible = true
        val userAgent = userAgentField.get(null) as String

        assertEquals("WLED-Android", userAgent, "User-Agent must be exactly 'WLED-Android'")

        val lambdaMethod = WebsocketClient::class.java.declaredMethods.first {
            it.name.contains("connectAndConsumeFrames\$lambda")
        }
        lambdaMethod.isAccessible = true
        val requestBuilder = io.ktor.client.request.HttpRequestBuilder()
        lambdaMethod.invoke(null, requestBuilder)

        assertEquals(
            "WLED-Android",
            requestBuilder.headers[io.ktor.http.HttpHeaders.UserAgent],
            "HttpRequestBuilder must have User-Agent set to 'WLED-Android'",
        )
    }

    // -------------------------------------------------------------------------
    // 3. Frame Decoding Resilience
    // -------------------------------------------------------------------------

    @Test
    @Suppress("LongMethod")
    fun `handleTextFrame successfully parses valid DeviceStateInfo JSON and updates state`() = runTest {
        val client = WebsocketClient(device, httpClient, json)
        val validJson = """
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
                  "maxseg": 32
                },
                "wifi": {
                  "bssid": "aa:bb:cc:dd:ee:ff",
                  "rssi": -72,
                  "signal": 56,
                  "channel": 1,
                  "ap": false
                }
              }
            }
        """.trimIndent()

        invokeHandleTextFrame(client, validJson)

        val stateInfo = client.stateInfo.value
        assertNotNull(stateInfo)
        assertEquals(true, stateInfo!!.state.isOn)
        assertEquals(195, stateInfo.state.brightness)
        assertEquals("16.0.1", stateInfo.info.version)
        assertEquals("WLED Desk", stateInfo.info.name)
    }

    @Test
    fun `handleTextFrame handles malformed, partial, and garbage JSON without throwing`() = runTest {
        val client = WebsocketClient(device, httpClient, json)

        val badPayloads = listOf(
            "",
            "   ",
            "not json at all",
            "<html><head><title>502 Bad Gateway</title></head></html>",
            "{",
            "{\"state\":",
            "{\"state\": {\"on\": true",
            "{\"state\": null, \"info\": null}",
            "12345",
            "true",
            "\"string value\"",
            "[1, 2, 3]",
            "{\"unknown_key\": 123}",
            "{\"state\": {\"on\": true}}", // Missing required 'info'
            "{\"info\": {\"ver\": \"0.14.0\"}}", // Missing required 'state'
        )

        for (payload in badPayloads) {
            // Must not throw any exception
            invokeHandleTextFrame(client, payload)
        }

        // State info must remain null
        assertNull(client.stateInfo.value)
    }

    @Test
    fun `handleTextFrame survives extra unknown fields with ignoreUnknownKeys`() = runTest {
        val client = WebsocketClient(device, httpClient, json)
        val jsonWithFutureFields = """
            {
              "state": {
                "on": false,
                "bri": 255,
                "future_state_field": "unknown",
                "seg": []
              },
              "info": {
                "ver": "0.15.0",
                "vid": 2401010,
                "name": "Living Room Future",
                "future_info_field": 99999,
                "leds": {
                  "count": 100,
                  "fps": 30,
                  "pwr": 500,
                  "maxpwr": 2000,
                  "maxseg": 10
                },
                "wifi": {
                  "bssid": "00:11:22:33:44:55",
                  "rssi": -60,
                  "signal": 80,
                  "channel": 6,
                  "ap": false
                }
              },
              "future_root_field": { "nested": true }
            }
        """.trimIndent()

        invokeHandleTextFrame(client, jsonWithFutureFields)

        val stateInfo = client.stateInfo.value
        assertNotNull(stateInfo)
        assertEquals(false, stateInfo!!.state.isOn)
        assertEquals(255, stateInfo.state.brightness)
        assertEquals("0.15.0", stateInfo.info.version)
    }

    // -------------------------------------------------------------------------
    // 4. Lifecycle and Concurrency Stress Testing
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
    fun `sendState rethrows CancellationException when outgoing channel is cancelled`() = runTest {
        val mockSession = mockk<DefaultClientWebSocketSession>()
        val outgoingChannel = Channel<Frame>(1)
        outgoingChannel.close(CancellationException("Test cancel"))

        every { mockSession.coroutineContext } returns
            kotlinx.coroutines.Job() + Dispatchers.Unconfined
        every { mockSession.outgoing } returns outgoingChannel
        coEvery { mockSession.send(any()) } throws CancellationException("Test cancel")

        val client = WebsocketClient(device, httpClient, json)
        val sessionField = WebsocketClient::class.java.getDeclaredField("currentSession")
        sessionField.isAccessible = true
        sessionField.set(client, mockSession)

        var cancellationThrown = false
        try {
            client.sendState(State(isOn = true))
        } catch (_: CancellationException) {
            cancellationThrown = true
        }
        assertTrue(cancellationThrown, "Expected CancellationException to be thrown")
    }

    @Test
    fun `sendState propagates TimeoutCancellationException when wrapped in withTimeout`() = runTest {
        val mockSession = mockk<DefaultClientWebSocketSession>()
        val outgoingChannel = Channel<Frame>(0)

        every { mockSession.coroutineContext } returns
            kotlinx.coroutines.Job() + Dispatchers.Unconfined
        every { mockSession.outgoing } returns outgoingChannel
        coEvery { mockSession.send(any()) } coAnswers {
            outgoingChannel.send(firstArg())
        }

        val client = WebsocketClient(device, httpClient, json)
        val sessionField = WebsocketClient::class.java.getDeclaredField("currentSession")
        sessionField.isAccessible = true
        sessionField.set(client, mockSession)

        var timeoutThrown = false
        try {
            withTimeout(50L) {
                client.sendState(State(isOn = true))
            }
        } catch (_: TimeoutCancellationException) {
            timeoutThrown = true
        }
        assertTrue(timeoutThrown, "Expected TimeoutCancellationException to be thrown")
    }

    @Test
    fun `sendState with withTimeoutOrNull returns null on timeout instead of false`() = runTest {
        val mockSession = mockk<DefaultClientWebSocketSession>()
        val outgoingChannel = Channel<Frame>(0)

        every { mockSession.coroutineContext } returns
            kotlinx.coroutines.Job() + Dispatchers.Unconfined
        every { mockSession.outgoing } returns outgoingChannel
        coEvery { mockSession.send(any()) } coAnswers {
            outgoingChannel.send(firstArg())
        }

        val client = WebsocketClient(device, httpClient, json)
        val sessionField = WebsocketClient::class.java.getDeclaredField("currentSession")
        sessionField.isAccessible = true
        sessionField.set(client, mockSession)

        val result = withTimeoutOrNull(50L) {
            client.sendState(State(isOn = true))
        }
        assertNull(result, "withTimeoutOrNull must evaluate to null on timeout")
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

        val neutralRandom = deterministicRandom(1.0)

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            random = neutralRandom,
        )

        client.connect()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.CONNECTED, client.status.value)
        assertEquals(1, connectionAttempts.get())

        // Simulate unexpected network drop on active connection
        session1Incoming.close()
        localScope.runCurrent()
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Because previous connection succeeded, retryCount was reset to 0.
        // With neutral jitter, attempt 0 delay is exactly 2000ms.
        // At 1999ms, attempt 2 must NOT have executed.
        localScope.advanceTimeBy(1999L)
        localScope.runCurrent()
        assertEquals(1, connectionAttempts.get())

        // At 2000ms, attempt 2 executes and fails.
        localScope.advanceTimeBy(1L)
        localScope.runCurrent()
        assertEquals(2, connectionAttempts.get())

        // Now retryCount is 1. Attempt 3 delay is 4000ms (scheduled at 6000ms).
        localScope.advanceTimeBy(3999L)
        localScope.runCurrent()
        assertEquals(2, connectionAttempts.get())

        localScope.advanceTimeBy(1L)
        localScope.runCurrent()
        assertEquals(3, connectionAttempts.get())

        client.destroy()
    }

    @Test
    fun `rapid disconnect followed immediately by connect does not clobber session or status from cancelled job`() =
        runTest {
            val localDispatcher = StandardTestDispatcher(testScheduler)
            val localScope = TestScope(localDispatcher)

            val session1Incoming = Channel<Frame>(Channel.UNLIMITED)
            val session1Outgoing = Channel<Frame>(Channel.UNLIMITED)
            val session1 = mockk<DefaultClientWebSocketSession>(relaxed = true)
            every { session1.incoming } returns session1Incoming
            every { session1.outgoing } returns session1Outgoing
            every { session1.coroutineContext } returns
                SupervisorJob() + Dispatchers.Default
            coEvery { session1.send(any()) } coAnswers {
                session1Outgoing.send(firstArg())
            }

            val session2Incoming = Channel<Frame>(Channel.UNLIMITED)
            val session2Outgoing = Channel<Frame>(Channel.UNLIMITED)
            val session2 = mockk<DefaultClientWebSocketSession>(relaxed = true)
            every { session2.incoming } returns session2Incoming
            every { session2.outgoing } returns session2Outgoing
            every { session2.coroutineContext } returns
                SupervisorJob() + Dispatchers.Default
            coEvery { session2.send(any()) } coAnswers {
                session2Outgoing.send(firstArg())
            }

            val sessions = listOf(session1, session2)
            val sessionIndex = AtomicInteger(0)

            coEvery { httpClient.webSocketSession(any<String>(), any()) } coAnswers {
                val idx = sessionIndex.getAndIncrement().coerceAtMost(sessions.size - 1)
                sessions[idx]
            }

            val client = WebsocketClient(
                device = device,
                httpClient = httpClient,
                json = json,
                coroutineDispatcher = localDispatcher,
                coroutineScope = localScope,
            )

            // 1. Establish initial connection
            client.connect()
            localScope.runCurrent()
            assertEquals(WebsocketStatus.CONNECTED, client.status.value)

            // 2. Rapid disconnect then connect
            client.disconnect()
            client.connect()

            // 3. Run all coroutines to completion
            localScope.runCurrent()

            // 4. Assert: Status must remain CONNECTED and sendState must succeed
            assertEquals(WebsocketStatus.CONNECTED, client.status.value, "Status must remain CONNECTED")
            val sendResult = client.sendState(State(isOn = true))
            assertTrue(sendResult, "sendState must succeed on second session")

            client.destroy()
        }

    @Test
    fun `calling connect while sleeping in backoff delay interrupts sleep and reconnects immediately`() = runTest {
        val localDispatcher = StandardTestDispatcher(testScheduler)
        val localScope = TestScope(localDispatcher)

        val connectionAttempts = AtomicInteger(0)
        val session = mockk<DefaultClientWebSocketSession>(relaxed = true)
        val incoming = Channel<Frame>(Channel.UNLIMITED)
        val outgoing = Channel<Frame>(Channel.UNLIMITED)
        every { session.incoming } returns incoming
        every { session.outgoing } returns outgoing
        every { session.coroutineContext } returns
            SupervisorJob() + Dispatchers.Default

        coEvery { httpClient.webSocketSession(any<String>(), any()) } coAnswers {
            val attempt = connectionAttempts.incrementAndGet()
            if (attempt == 1) {
                throw IOException("Initial connect failed")
            } else {
                session
            }
        }

        val neutralRandom = deterministicRandom(1.0)

        val client = WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = localDispatcher,
            coroutineScope = localScope,
            random = neutralRandom,
        )

        client.connect()
        localScope.runCurrent()

        // Attempt 1 failed. Status is DISCONNECTED. Client is sleeping in backoff delay (2000ms).
        assertEquals(1, connectionAttempts.get())
        assertEquals(WebsocketStatus.DISCONNECTED, client.status.value)

        // Advance only 100ms (far short of the 2000ms delay).
        localScope.advanceTimeBy(100L)
        localScope.runCurrent()
        assertEquals(1, connectionAttempts.get())

        // User / ViewModel triggers manual connect to expedite reconnection
        client.connect()
        localScope.runCurrent()

        // Reconnection attempt 2 must run immediately without waiting for remaining 1900ms
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

    // -------------------------------------------------------------------------
    // Reflection Helpers
    // -------------------------------------------------------------------------

    private fun invokeBuildWebsocketUrl(client: WebsocketClient, address: String): String {
        val method: Method = WebsocketClient::class.java.getDeclaredMethod("buildWebsocketUrl", String::class.java)
        method.isAccessible = true
        return method.invoke(client, address) as String
    }

    private fun invokeHandleTextFrame(client: WebsocketClient, text: String) {
        val method: Method = WebsocketClient::class.java.declaredMethods.first { it.name == "handleTextFrame" }
        method.isAccessible = true
        val continuation = object : Continuation<Unit> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                // No-op for reflection test invocation
            }
        }
        method.invoke(client, text, continuation)
    }

    private fun deterministicRandom(fixedValue: Double): Random = object : Random() {
        override fun nextBits(bitCount: Int): Int = 0
        override fun nextDouble(from: Double, until: Double): Double =
            (from + (until - from) * ((fixedValue - 0.75) / 0.5)).coerceIn(from, until)
    }
}
