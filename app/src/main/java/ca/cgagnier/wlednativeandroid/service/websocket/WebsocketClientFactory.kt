package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating WebsocketClient instances.
 * Encapsulates the network dependencies required for WebSocket connections.
 */
@Singleton
class WebsocketClientFactory @Inject constructor(private val httpClient: HttpClient, private val json: Json) {
    /**
     * Creates a new WebsocketClient for the given device.
     */
    fun create(device: Device): WebsocketClient = WebsocketClient(
        device = device,
        httpClient = httpClient,
        json = json,
    )
}
