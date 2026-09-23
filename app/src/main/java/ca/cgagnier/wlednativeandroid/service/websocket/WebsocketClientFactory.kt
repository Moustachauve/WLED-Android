package ca.cgagnier.wlednativeandroid.service.websocket

import android.content.Context
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory for creating WebsocketClient instances.
 * Encapsulates the dependencies required for WebSocket connections.
 */
@Suppress("LongParameterList")
@Singleton
class WebsocketClientFactory @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val deviceRepository: DeviceRepository,
    private val widgetManager: WledWidgetManager,
    private val deviceUpdateManager: DeviceUpdateManager,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
    private val repositoryDao: RepositoryDao,
) {
    /**
     * Creates a new WebsocketClient for the given device.
     */
    fun create(device: Device): WebsocketClient = WebsocketClient(
        device = device,
        applicationContext = applicationContext,
        deviceRepository = deviceRepository,
        widgetManager = widgetManager,
        deviceUpdateManager = deviceUpdateManager,
        okHttpClient = okHttpClient,
        json = json,
        repositoryDao = repositoryDao,
    )
}
