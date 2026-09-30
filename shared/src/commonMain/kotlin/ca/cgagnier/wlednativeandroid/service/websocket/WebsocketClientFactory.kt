package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

/**
 * Factory for creating WebsocketClient instances.
 * Encapsulates the network dependencies required for WebSocket connections.
 */
class WebsocketClientFactory(
    private val httpClient: HttpClient,
    private val json: Json,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Creates a new WebsocketClient for the given device.
     */
    fun create(device: Device, coroutineScope: CoroutineScope? = null): WebsocketClient = WebsocketClient(
        device = device,
        httpClient = httpClient,
        json = json,
        coroutineDispatcher = dispatcher,
        coroutineScope = coroutineScope ?: CoroutineScope(SupervisorJob() + dispatcher),
    )
}
