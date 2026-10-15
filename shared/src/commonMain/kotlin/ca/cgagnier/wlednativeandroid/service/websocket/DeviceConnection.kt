package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract representing an active or managed real-time connection to a WLED device.
 */
interface DeviceConnection {
    val status: StateFlow<WebsocketStatus>
    val incomingStateInfo: SharedFlow<DeviceStateInfo>
    fun connect()
    fun pause()
    fun resume()
    fun destroy()
    suspend fun sendState(state: State): Boolean
}

/**
 * Factory for creating [DeviceConnection] instances for given network addresses.
 */
fun interface DeviceConnectionFactory {
    fun createConnection(address: String, scope: CoroutineScope): DeviceConnection
}
