package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * No-op [DeviceConnectionFactory] for unit tests and UI previews that should not perform network I/O.
 */
object NoOpDeviceConnectionFactory : DeviceConnectionFactory {
    override fun createConnection(address: String, scope: CoroutineScope): DeviceConnection = NoOpDeviceConnection()
}

private class NoOpDeviceConnection : DeviceConnection {
    override val status: StateFlow<WebsocketStatus> = MutableStateFlow(WebsocketStatus.DISCONNECTED)
    override val incomingStateInfo: SharedFlow<DeviceStateInfo> = MutableSharedFlow()
    override fun connect() = Unit
    override fun pause() = Unit
    override fun resume() = Unit
    override fun destroy() = Unit
    override suspend fun sendState(state: State): Boolean = false
}
