package ca.cgagnier.wlednativeandroid.service.websocket

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Pure Ktor WebSocket client for WLED devices.
 *
 * Manages connection lifecycle, frame encode/decode, exponential backoff with jitter,
 * and reactive state exposure via Kotlin Coroutines and Flows.
 */
class WebsocketClient(
    device: Device,
    private val httpClient: HttpClient,
    private val json: Json,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher),
    private val random: Random = Random.Default,
) : WebsocketClientContract {

    @Volatile
    override var device: Device = device
        private set

    private val _status = MutableStateFlow(WebsocketStatus.DISCONNECTED)
    override val status: StateFlow<WebsocketStatus> = _status.asStateFlow()

    private val _incomingStateInfo = MutableSharedFlow<DeviceStateInfo>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val incomingStateInfo: SharedFlow<DeviceStateInfo> = _incomingStateInfo.asSharedFlow()

    private val _stateInfo = MutableStateFlow<DeviceStateInfo?>(null)
    override val stateInfo: StateFlow<DeviceStateInfo?> = _stateInfo.asStateFlow()

    private val isManuallyDisconnected = AtomicBoolean(false)
    private val sendMutex = Mutex()

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    private var currentSession: DefaultClientWebSocketSession? = null

    override fun connect() {
        synchronized(this) {
            isManuallyDisconnected.set(false)
            val currentJob = connectionJob
            if (currentJob?.isActive == true) {
                if (_status.value == WebsocketStatus.DISCONNECTED) {
                    Log.d(TAG, "Expediting reconnection for ${device.address}: cancelling backoff delay")
                    currentJob.cancel(CancellationException("Manual connect during backoff delay"))
                } else {
                    Log.d(TAG, "Connection already active or connecting for ${device.address}")
                    return
                }
            }
            _status.value = WebsocketStatus.CONNECTING
            connectionJob = coroutineScope.launch(coroutineDispatcher) {
                runConnectionLoop()
            }
        }
    }

    override fun disconnect() {
        Log.d(TAG, "Manually disconnecting from ${device.address}")
        val sessionToClose: DefaultClientWebSocketSession?
        synchronized(this) {
            isManuallyDisconnected.set(true)
            connectionJob?.cancel(CancellationException("Manual disconnect"))
            connectionJob = null
            sessionToClose = currentSession
            currentSession = null
            _status.value = WebsocketStatus.DISCONNECTED
        }

        if (sessionToClose != null) {
            coroutineScope.launch {
                withContext(NonCancellable) {
                    try {
                        sessionToClose.close(
                            CloseReason(
                                CloseReason.Codes.NORMAL,
                                "Client manual disconnect",
                            ),
                        )
                    } catch (_: Exception) {
                        // Ignore failure during close
                    }
                }
            }
        }
    }

    fun updateDevice(newDevice: Device) {
        this.device = newDevice
    }

    fun destroy() {
        Log.d(TAG, "Destroying WebsocketClient for ${device.address}")
        disconnect()
        coroutineScope.cancel()
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runConnectionLoop() {
        var retryCount = 0
        while (coroutineContext.isActive && !isManuallyDisconnected.get()) {
            val wasConnected = connectAndConsumeFrames(retryCount)
            if (wasConnected) {
                retryCount = 0
            }

            if (coroutineContext.isActive && !isManuallyDisconnected.get()) {
                val backoffMs = calculateBackoffWithJitter(retryCount, random = random)
                Log.d(TAG, "Reconnecting to ${device.address} in ${backoffMs}ms (retry $retryCount)")
                retryCount++
                try {
                    delay(backoffMs)
                } catch (e: CancellationException) {
                    Log.d(TAG, "Backoff delay cancelled for ${device.address}")
                    throw e
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun connectAndConsumeFrames(retryCount: Int): Boolean {
        val myJob = coroutineContext[Job]
        val shouldProceed = synchronized(this) {
            if (coroutineContext.isActive && connectionJob === myJob && !isManuallyDisconnected.get()) {
                _status.value = WebsocketStatus.CONNECTING
                true
            } else {
                false
            }
        }
        if (!shouldProceed) {
            throw CancellationException("Connection superseded or manually cancelled before attempt")
        }
        var session: DefaultClientWebSocketSession? = null
        var wasConnected = false

        try {
            val url = buildWebsocketUrl(device.address)
            Log.d(TAG, "Connecting to $url (attempt $retryCount)")

            session = httpClient.webSocketSession(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
            }

            val sessionBound = synchronized(this) {
                if (coroutineContext.isActive && connectionJob === myJob && !isManuallyDisconnected.get()) {
                    currentSession = session
                    _status.value = WebsocketStatus.CONNECTED
                    wasConnected = true
                    true
                } else {
                    false
                }
            }

            if (!sessionBound) {
                throw CancellationException("Connection superseded or cancelled during handshake")
            }

            consumeIncomingFrames(session)
            Log.d(TAG, "WebSocket incoming channel completed for ${device.address}")
        } catch (e: CancellationException) {
            Log.d(TAG, "Connection loop cancelled for ${device.address}: ${e.message}")
            throw e
        } catch (e: ClosedReceiveChannelException) {
            Log.w(TAG, "WebSocket channel closed for ${device.address}: ${e.message}")
        } catch (e: IOException) {
            Log.w(TAG, "WebSocket IO exception for ${device.address}: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected WebSocket error for ${device.address}: ${e.message}", e)
        } finally {
            cleanupSession(session, myJob)
        }
        return wasConnected
    }

    private suspend fun cleanupSession(session: DefaultClientWebSocketSession?, myJob: Job?) {
        synchronized(this) {
            if (currentSession === session) {
                currentSession = null
            }
        }
        withContext(NonCancellable) {
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Session ended"))
            } catch (_: Exception) {
                // Ignore failure during close
            }
        }
        synchronized(this) {
            if (!isManuallyDisconnected.get() && connectionJob === myJob) {
                _status.value = WebsocketStatus.DISCONNECTED
            }
        }
    }

    private suspend fun consumeIncomingFrames(session: DefaultClientWebSocketSession) {
        for (frame in session.incoming) {
            when (frame) {
                is Frame.Text -> handleTextFrame(frame.readText())

                is Frame.Binary -> {
                    Log.d(TAG, "Received binary frame from ${device.address}")
                }

                is Frame.Close -> {
                    val reason = frame.readReason()
                    Log.d(TAG, "Received Close frame from ${device.address}: $reason")
                    break
                }

                is Frame.Ping, is Frame.Pong -> {
                    // Handled automatically by Ktor WebSockets ping plugin
                }
            }
        }
    }

    internal suspend fun handleTextFrame(text: String) {
        Log.d(TAG, "Received frame from ${device.address}: $text")
        try {
            val decodedStateInfo = json.decodeFromString<DeviceStateInfo>(text)
            _stateInfo.value = decodedStateInfo
            _incomingStateInfo.emit(decodedStateInfo)
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse JSON frame from ${device.address}: $text", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Illegal argument parsing JSON frame from ${device.address}: $text", e)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    override suspend fun sendState(state: State): Boolean = sendMutex.withLock {
        val session = currentSession
        if (session == null || !session.isActive) {
            Log.w(TAG, "Cannot send state: WebSocket not connected to ${device.address}")
            if (_status.value != WebsocketStatus.CONNECTED && !isManuallyDisconnected.get()) {
                connect()
            }
            return false
        }

        return try {
            val jsonString = json.encodeToString(state)
            Log.d(TAG, "Sending state to ${device.address}: $jsonString")
            session.send(Frame.Text(jsonString))
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send state to ${device.address}", e)
            false
        }
    }

    fun sendStateAsync(state: State) {
        coroutineScope.launch {
            sendState(state)
        }
    }

    internal fun buildWebsocketUrl(address: String): String {
        val cleanAddress = address
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")
            .trimEnd('/')
        return "ws://$cleanAddress/$WEBSOCKET_PATH"
    }

    companion object {
        internal const val TAG = "WebsocketClient"
        internal const val WEBSOCKET_PATH = "ws"
        internal const val USER_AGENT = "WLED-Android"
        private const val BASE_BACKOFF_MS = 2000L
        private const val MAX_BACKOFF_MS = 60000L
        private const val JITTER_RATIO = 0.25
        private const val MAX_RETRY_EXPONENT = 30
        private const val BUFFER_CAPACITY = 64

        fun calculateBackoffWithJitter(
            retryCount: Int,
            baseDelayMs: Long = BASE_BACKOFF_MS,
            maxDelayMs: Long = MAX_BACKOFF_MS,
            jitterRatio: Double = JITTER_RATIO,
            random: Random = Random.Default,
        ): Long {
            require(retryCount >= 0) { "retryCount must be non-negative" }
            val effectiveExponent = retryCount.coerceAtMost(MAX_RETRY_EXPONENT)
            val rawExponential = baseDelayMs * (1L shl effectiveExponent)
            val cappedExponential = rawExponential.coerceAtMost(maxDelayMs)

            val minFactor = (1.0 - jitterRatio).coerceAtLeast(0.0)
            val maxFactor = 1.0 + jitterRatio
            val jitterFactor = random.nextDouble(minFactor, maxFactor)

            return (cappedExponential * jitterFactor).toLong().coerceIn(0L, maxDelayMs)
        }
    }
}

interface WebsocketClientContract {
    val device: Device
    val status: StateFlow<WebsocketStatus>
    val incomingStateInfo: SharedFlow<DeviceStateInfo>
    val stateInfo: StateFlow<DeviceStateInfo?>
    fun connect()
    fun disconnect()
    suspend fun sendState(state: State): Boolean
}
