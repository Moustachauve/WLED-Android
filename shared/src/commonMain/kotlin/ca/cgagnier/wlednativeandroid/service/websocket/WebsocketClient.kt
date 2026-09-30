package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.shared.SynchronizedObject
import ca.cgagnier.wlednativeandroid.shared.ioDispatcher
import ca.cgagnier.wlednativeandroid.shared.synchronized
import co.touchlab.kermit.Logger
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okio.IOException
import kotlin.concurrent.Volatile
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

private const val TAG = "WebsocketClient"
private val logger = Logger.withTag(TAG)

/**
 * Pure Ktor WebSocket client for WLED devices.
 *
 * Manages connection lifecycle, frame encode/decode, exponential backoff with jitter,
 * and reactive state exposure via Kotlin Coroutines and Flows.
 */
@Suppress("LongParameterList")
class WebsocketClient(
    device: Device,
    private val httpClient: HttpClient,
    private val json: Json,
    private val coroutineDispatcher: CoroutineDispatcher = ioDispatcher,
    coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + coroutineDispatcher),
    private val random: Random = Random.Default,
    private val sessionOpener: suspend (HttpClient, String) -> DefaultClientWebSocketSession = { client, url ->
        client.webSocketSession(url) {
            header(HttpHeaders.UserAgent, USER_AGENT)
        }
    },
) {

    @Volatile
    var device: Device = device
        private set

    private val clientJob = SupervisorJob(coroutineScope.coroutineContext[Job])
    private val clientScope = CoroutineScope(coroutineScope.coroutineContext + clientJob)

    private val _status = MutableStateFlow(WebsocketStatus.DISCONNECTED)
    val status: StateFlow<WebsocketStatus> = _status.asStateFlow()

    private val _incomingStateInfo = MutableSharedFlow<DeviceStateInfo>(
        replay = 0,
        extraBufferCapacity = BUFFER_CAPACITY,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val incomingStateInfo: SharedFlow<DeviceStateInfo> = _incomingStateInfo.asSharedFlow()

    private val stateLock = SynchronizedObject()
    private var isManuallyDisconnected = false
    private var isPaused = false
    private var isDestroyed = false

    @Volatile
    private var connectionJob: Job? = null

    @Volatile
    private var currentSession: DefaultClientWebSocketSession? = null

    fun connect() {
        var oldJob: Job? = null
        var shouldLaunch = false
        synchronized(stateLock) {
            if (isDestroyed) {
                logger.w { "Cannot connect: WebsocketClient for ${device.address} has been destroyed" }
                return
            }
            isManuallyDisconnected = false
            isPaused = false
            val currentJob = connectionJob
            if (currentJob?.isActive == true) {
                if (_status.value == WebsocketStatus.DISCONNECTED) {
                    logger.d { "Expediting reconnection for ${device.address}: cancelling backoff delay" }
                    currentJob.cancel(CancellationException("Manual connect during backoff delay"))
                    oldJob = currentJob
                } else {
                    logger.d { "Connection already active or connecting for ${device.address}" }
                    return
                }
            } else {
                oldJob = currentJob
            }
            shouldLaunch = true
            connectionJob = clientScope.launch(coroutineDispatcher) {
                oldJob?.join()
                runConnectionLoop()
            }
        }
        if (shouldLaunch) {
            _status.value = WebsocketStatus.CONNECTING
        }
    }

    fun disconnect() {
        logger.d { "Manually disconnecting from ${device.address}" }
        var jobToCancel: Job? = null
        var sessionToCancel: DefaultClientWebSocketSession? = null
        synchronized(stateLock) {
            isManuallyDisconnected = true
            jobToCancel = connectionJob
            sessionToCancel = currentSession
            currentSession = null
        }
        sessionToCancel?.cancel(CancellationException("Manual disconnect"))
        jobToCancel?.cancel(CancellationException("Manual disconnect"))
        _status.value = WebsocketStatus.DISCONNECTED
    }

    fun pause() {
        logger.d { "Pausing connection for ${device.address}" }
        var jobToCancel: Job? = null
        var sessionToCancel: DefaultClientWebSocketSession? = null
        synchronized(stateLock) {
            isPaused = true
            jobToCancel = connectionJob
            sessionToCancel = currentSession
            currentSession = null
        }
        sessionToCancel?.cancel(CancellationException("App paused"))
        jobToCancel?.cancel(CancellationException("App paused"))
        _status.value = WebsocketStatus.DISCONNECTED
    }

    fun resume() {
        val shouldReturn = synchronized(stateLock) {
            if (isManuallyDisconnected || isDestroyed) {
                true
            } else {
                isPaused = false
                false
            }
        }
        if (shouldReturn) {
            logger.d { "Not resuming ${device.address}: manually disconnected or destroyed" }
            return
        }
        connect()
    }

    fun updateDevice(newDevice: Device) {
        this.device = newDevice
    }

    fun destroy() {
        logger.d { "Destroying WebsocketClient for ${device.address}" }
        synchronized(stateLock) { isDestroyed = true }
        disconnect()
        clientJob.cancel()
    }

    private fun canConnect(): Boolean = synchronized(stateLock) {
        !isManuallyDisconnected && !isPaused
    }

    private fun isConnectionActive(isContextActive: Boolean, myJob: Job?): Boolean =
        isContextActive && connectionJob === myJob && canConnect()

    @Suppress("TooGenericExceptionCaught")
    private suspend fun runConnectionLoop() {
        var retryCount = 0
        while (coroutineContext.isActive && canConnect()) {
            val wasConnected = connectAndConsumeFrames(retryCount)
            if (wasConnected) {
                retryCount = 0
            }

            if (coroutineContext.isActive && canConnect()) {
                val backoffMs = calculateBackoffWithJitter(retryCount, random = random)
                logger.d { "Reconnecting to ${device.address} in ${backoffMs}ms (retry $retryCount)" }
                retryCount++
                try {
                    delay(backoffMs)
                } catch (e: CancellationException) {
                    logger.d { "Backoff delay cancelled for ${device.address}" }
                    throw e
                }
            }
        }
    }

    @Suppress("TooGenericExceptionCaught", "ThrowsCount")
    private suspend fun connectAndConsumeFrames(retryCount: Int): Boolean {
        val myJob = coroutineContext[Job]
        val isCurrentActive = coroutineContext.isActive
        val shouldProceed = synchronized(stateLock) {
            isConnectionActive(isCurrentActive, myJob)
        }
        if (!shouldProceed) {
            throw CancellationException("Connection superseded or manually cancelled before attempt")
        }
        _status.value = WebsocketStatus.CONNECTING
        var session: DefaultClientWebSocketSession? = null
        var wasConnected = false

        try {
            val url = buildWebsocketUrl(device.address)
            logger.d { "Connecting to $url (attempt $retryCount)" }

            session = sessionOpener(httpClient, url)

            val isBoundActive = coroutineContext.isActive
            val sessionBound = synchronized(stateLock) {
                if (isConnectionActive(isBoundActive, myJob)) {
                    currentSession = session
                    wasConnected = true
                    true
                } else {
                    false
                }
            }

            if (!sessionBound) {
                throw CancellationException("Connection superseded or cancelled during handshake")
            }
            _status.value = WebsocketStatus.CONNECTED

            consumeIncomingFrames(session)
            logger.d { "WebSocket incoming channel completed for ${device.address}" }
        } catch (e: CancellationException) {
            logger.d { "Connection loop cancelled for ${device.address}: ${e.message}" }
            throw e
        } catch (e: ClosedReceiveChannelException) {
            logger.w { "WebSocket channel closed for ${device.address}: ${e.message}" }
        } catch (e: IOException) {
            logger.w(e) { "WebSocket IO exception for ${device.address}" }
        } catch (e: Exception) {
            logger.w(e) { "Unexpected WebSocket error for ${device.address}" }
        } finally {
            cleanupSession(session, myJob)
        }
        return wasConnected
    }

    private suspend fun cleanupSession(session: DefaultClientWebSocketSession?, myJob: Job?) {
        var shouldSetDisconnected = false
        synchronized(stateLock) {
            if (currentSession === session) {
                currentSession = null
            }
            if (canConnect() && connectionJob === myJob) {
                shouldSetDisconnected = true
            }
        }
        if (shouldSetDisconnected) {
            _status.value = WebsocketStatus.DISCONNECTED
        }
        withContext(NonCancellable) {
            try {
                val closedGracefully = withTimeoutOrNull(CLOSE_TIMEOUT_MS) {
                    session?.close(CloseReason(CloseReason.Codes.NORMAL, "Session ended"))
                    true
                }
                if (closedGracefully != true) {
                    session?.cancel(CancellationException("Session close timed out"))
                }
            } catch (e: Exception) {
                session?.cancel(CancellationException("Session close failed", e))
            }
        }
    }

    private suspend fun consumeIncomingFrames(session: DefaultClientWebSocketSession) {
        for (frame in session.incoming) {
            @Suppress("RedundantElseInWhen")
            when (frame) {
                is Frame.Text -> handleTextFrame(frame.readText())

                is Frame.Binary -> {
                    logger.d { "Received binary frame from ${device.address}" }
                }

                is Frame.Close -> {
                    val reason = frame.readReason()
                    logger.d { "Received Close frame from ${device.address}: $reason" }
                    break
                }

                is Frame.Ping, is Frame.Pong -> {
                    // Handled automatically by Ktor WebSockets ping plugin
                }

                else -> Unit
            }
        }
    }

    suspend fun handleTextFrame(text: String) {
        logger.d { "Received frame from ${device.address}: ${truncatePayload(text)}" }
        try {
            val decodedStateInfo = json.decodeFromString<DeviceStateInfo>(text)
            _incomingStateInfo.emit(decodedStateInfo)
        } catch (e: SerializationException) {
            logger.e(e) { "Failed to parse JSON frame from ${device.address}: ${truncatePayload(text)}" }
        } catch (e: IllegalArgumentException) {
            logger.e(e) { "Illegal argument parsing JSON frame from ${device.address}: ${truncatePayload(text)}" }
        }
    }

    private fun truncatePayload(payload: String): String = if (payload.length <=
        MAX_LOG_PAYLOAD_LENGTH
    ) {
        payload
    } else {
        "${payload.take(MAX_LOG_PAYLOAD_LENGTH)}... (${payload.length} chars)"
    }

    @Suppress("TooGenericExceptionCaught")
    suspend fun sendState(state: State): Boolean {
        val destroyed = synchronized(stateLock) { isDestroyed }
        if (destroyed) {
            logger.w { "Cannot send state: WebsocketClient for ${device.address} has been destroyed" }
            return false
        }
        var session = currentSession
        if (session == null || !session.isActive) {
            if (_status.value == WebsocketStatus.DISCONNECTED && canConnect()) {
                connect()
            }
            if (_status.value == WebsocketStatus.CONNECTING) {
                withTimeoutOrNull(AWAIT_CONNECT_TIMEOUT_MS) {
                    _status.first { it != WebsocketStatus.CONNECTING }
                }
                session = currentSession
            }
        }

        if (session == null || !session.isActive) {
            logger.w { "Cannot send state: WebSocket not connected to ${device.address}" }
            return false
        }

        return try {
            val jsonString = json.encodeToString(state)
            logger.d { "Sending state to ${device.address}: ${truncatePayload(jsonString)}" }
            session.send(Frame.Text(jsonString))
            true
        } catch (e: CancellationException) {
            if (!coroutineContext.isActive) throw e
            logger.w { "WebSocket session was cancelled while sending state to ${device.address}: ${e.message}" }
            false
        } catch (e: Exception) {
            logger.e(e) { "Failed to send state to ${device.address}" }
            false
        }
    }

    fun buildWebsocketUrl(address: String): String {
        val trimmedAddress = address.trim()
        val isSecure = trimmedAddress.startsWith("https://", ignoreCase = true) ||
            trimmedAddress.startsWith("wss://", ignoreCase = true)
        val scheme = if (isSecure) "wss://" else "ws://"

        val cleanAddress = trimmedAddress
            .replace(PROTOCOL_REGEX, "")
            .trimEnd('/')

        val hostAndPort = if (cleanAddress.endsWith("/$WEBSOCKET_PATH", ignoreCase = true)) {
            cleanAddress.substring(0, cleanAddress.length - WEBSOCKET_PATH.length - 1).trimEnd('/')
        } else {
            cleanAddress
        }

        return "$scheme$hostAndPort/$WEBSOCKET_PATH"
    }

    companion object {
        const val WEBSOCKET_PATH = "ws"
        const val USER_AGENT = "WLED-Android"
        private val PROTOCOL_REGEX = Regex("^(https?|wss?)://", RegexOption.IGNORE_CASE)
        const val BASE_BACKOFF_MS = 2000L
        const val MAX_BACKOFF_MS = 60000L
        const val CLOSE_TIMEOUT_MS = 1000L
        const val AWAIT_CONNECT_TIMEOUT_MS = 2000L
        const val MAX_LOG_PAYLOAD_LENGTH = 256
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

            val minDelay = (rawExponential * (1.0 - jitterRatio)).toLong()
                .coerceAtMost((maxDelayMs * (1.0 - jitterRatio)).toLong())
                .coerceAtLeast(0L)
            val maxDelay = (rawExponential * (1.0 + jitterRatio)).toLong()
                .coerceAtMost(maxDelayMs)
                .coerceAtLeast(minDelay)

            if (minDelay >= maxDelay) return maxDelay

            val factor = random.nextDouble(0.0, 1.0)
            return minDelay + ((maxDelay - minDelay) * factor).toLong()
        }
    }
}
