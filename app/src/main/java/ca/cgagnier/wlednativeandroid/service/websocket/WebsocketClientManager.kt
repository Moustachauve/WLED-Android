package ca.cgagnier.wlednativeandroid.service.websocket

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the active WebSocket client instances.
 * This is a singleton that provides access to all active websocket clients.
 */
@Singleton
class WebsocketClientManager @Inject constructor() {
    private val _clients = MutableStateFlow<Map<String, WebsocketClient>>(emptyMap())
    val clients: StateFlow<Map<String, WebsocketClient>> = _clients.asStateFlow()

    fun updateClients(newClients: Map<String, WebsocketClient>) {
        _clients.value = newClients
    }

    fun getClients(): Map<String, WebsocketClient> = _clients.value
}
