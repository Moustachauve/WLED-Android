package ca.cgagnier.wlednativeandroid.service.websocket

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

class WebsocketClient(
    device: Device,
    private val deviceRepository: DeviceRepository,
    deviceUpdateManager: DeviceUpdateManager
) {

    val deviceState: DeviceWithState = DeviceWithState(device, deviceUpdateManager)

    private var webSocket: WebSocket? = null
    private val client: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)
        .build()

    private var isManuallyDisconnected = false
    private var isConnecting = false
    private var retryCount = 0


    // Moshi setup
    private val moshi: Moshi = Moshi.Builder().build()
    private val deviceStateInfoJsonAdapter: JsonAdapter<DeviceStateInfo> =
        moshi.adapter(DeviceStateInfo::class.java)
    private val stateJsonAdapter: JsonAdapter<State> = moshi.adapter(State::class.java)

    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private const val TAG = "WebsocketClient"
        private const val RECONNECTION_DELAY = 2500L // 2.5 seconds
        private const val MAX_RECONNECTION_DELAY = 60000L // 60 seconds
        private const val NORMAL_CLOSURE_STATUS = 1000
    }

    private val webSocketListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket connected for ${deviceState.device.address}")
            deviceState.isWebsocketConnected.value = true
            retryCount = 0
            isConnecting = false
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            Log.d(TAG, "onMessage for ${deviceState.device.address}: $text")
            try {
                // Use the pre-created Moshi adapter
                val deviceStateInfo = deviceStateInfoJsonAdapter.fromJson(text)
                if (deviceStateInfo != null) {
                    deviceState.stateInfo.value = deviceStateInfo

                    // Update information about the device when we receive a message.
                    // Ideally, this should probably not be done in the client directly
                    coroutineScope.launch {
                        var branch = deviceState.device.branch
                        if (branch == Branch.UNKNOWN) {
                            branch = if (deviceStateInfo.info.version?.contains("-b") ?: false) {
                                Branch.BETA
                            } else {
                                Branch.STABLE
                            }
                        }
                        val newDevice = deviceState.device.copy(
                            originalName = deviceStateInfo.info.name,
                            address = deviceState.device.address,
                            lastSeen = System.currentTimeMillis(),
                            branch = branch,
                        )
                        deviceRepository.update(newDevice)
                    }
                } else {
                    Log.w(TAG, "Received a null message after parsing.")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to parse JSON from WebSocket", e)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(
                TAG,
                "WebSocket closing for ${deviceState.device.address}. Code: $code, Reason: $reason"
            )
            deviceState.isWebsocketConnected.value = false
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(
                TAG,
                "WebSocket failure for ${deviceState.device.address}: ${t.message}",
                t
            )
            this@WebsocketClient.webSocket = null
            deviceState.isWebsocketConnected.value = false
            isConnecting = false
            reconnect()
        }
    }

    /**
     * Updates the device state with a new device.
     * @param newDevice The new device to update with.
     */
    fun updateDevice(newDevice: Device) {
        deviceState.device = newDevice
    }

    fun connect() {
        if (webSocket != null || isConnecting) {
            Log.w(TAG, "Already connected or connecting to ${deviceState.device.address}")
            return
        }
        isManuallyDisconnected = false
        isConnecting = true
        val websocketUrl = "ws://${deviceState.device.address}/ws"
        val request = Request.Builder().url(websocketUrl).build()
        Log.d(TAG, "Connecting to ${deviceState.device.address}")
        webSocket = client.newWebSocket(request, webSocketListener)
    }

    fun disconnect() {
        Log.d(TAG, "Manually disconnecting from ${deviceState.device.address}")
        isManuallyDisconnected = true
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Client disconnected")
        webSocket = null
        // Ensure state is updated immediately
        deviceState.isWebsocketConnected.value = false
        isConnecting = false
    }


    private fun reconnect() {
        if (isManuallyDisconnected || isConnecting) return

        coroutineScope.launch {
            val delay = min(
                RECONNECTION_DELAY * 2.0.pow(retryCount).toLong(),
                MAX_RECONNECTION_DELAY
            )
            Log.d(TAG, "Reconnecting to ${deviceState.device.address} in ${delay / 1000}s")
            delay(delay)
            retryCount++
            connect()
        }
    }

    /**
     * Sends a message to the device.
     * @param message The message to send.
     */
    private fun sendMessage(message: String): Boolean {
        Log.d(TAG, "Sending message to ${deviceState.device.address}: $message")
        return webSocket?.send(message) ?: false
    }

    /**
     * Sends a State object to the device.
     * @param state The State object to send.
     */
    fun sendState(state: State) {
        // Trying to update the state when the device is offline should trigger a reconnection.
        // This is so that a user playing with the UI causes the device to reconnect if it
        // isn't trying to reconnect automatically for some reason.
        if (!deviceState.isWebsocketConnected.value) {
            Log.w(TAG, "Not connected to ${deviceState.device.address}")
            connect()
        }
        val json = stateJsonAdapter.toJson(state)
        sendMessage(json)
    }


    fun destroy() {
        Log.d(TAG, "Websocket client is destroyed for ${deviceState.device.address}")
        disconnect()
    }
}