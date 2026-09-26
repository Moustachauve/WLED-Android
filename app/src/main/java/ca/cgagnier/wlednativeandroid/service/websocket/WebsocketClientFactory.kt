package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.di.IoDispatcher
import ca.cgagnier.wlednativeandroid.model.Device
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating WebsocketClient instances.
 * Encapsulates the network dependencies required for WebSocket connections.
 */
@Singleton
class WebsocketClientFactory @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Creates a new WebsocketClient for the given device.
     */
    fun create(device: Device, coroutineScope: CoroutineScope? = null): WebsocketClient = if (coroutineScope != null) {
        WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = ioDispatcher,
            coroutineScope = coroutineScope,
        )
    } else {
        WebsocketClient(
            device = device,
            httpClient = httpClient,
            json = json,
            coroutineDispatcher = ioDispatcher,
        )
    }
}
