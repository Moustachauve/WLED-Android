package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.createDeviceRepository
import ca.cgagnier.wlednativeandroid.repository.getOrCreateRepositoryId
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import ca.cgagnier.wlednativeandroid.service.api.defaultJson
import ca.cgagnier.wlednativeandroid.service.update.RepositoryIdResolver
import ca.cgagnier.wlednativeandroid.service.update.UpdateTagResolver
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob

/**
 * Creates a [DeviceConnectionManager] for the iOS app.
 *
 * This is the Swift entry point. Pass `nil` to use the production websocket factory and default config.
 *
 * Interim wiring: the iOS app does not start Koin yet, so this mirrors `sharedDomainModule`. Once `AppDatabase`
 * resolves its dependencies through Koin, this factory can be replaced by `KoinHelper.deviceConnectionManager`.
 */
fun DeviceConnectionManager.Companion.create(
    database: DevicesDatabase,
    connectionFactory: DeviceConnectionFactory?,
    config: DeviceConnectionManagerConfig?,
): DeviceConnectionManager = create(
    database = database,
    connectionFactory = connectionFactory,
    config = config ?: DeviceConnectionManagerConfig(),
    connectivity = null,
)

@Suppress("LongParameterList")
internal fun DeviceConnectionManager.Companion.create(
    database: DevicesDatabase,
    connectionFactory: DeviceConnectionFactory?,
    config: DeviceConnectionManagerConfig,
    connectivity: NetworkConnectivityManager?,
    coroutineScope: CoroutineScope? = null,
    httpClient: HttpClient? = null,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): DeviceConnectionManager {
    val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + backgroundDispatcher)
    // Only build an HttpClient when the production websocket factory actually needs one.
    val factory = connectionFactory ?: WebsocketClientFactory(
        httpClient = httpClient ?: DeviceApiFactory.createHttpClient(),
        json = defaultJson,
        dispatcher = ioDispatcher,
    )
    // TODO: Use DeviceUpdateManager once iOS release data is stored in Room. Until then, update availability on
    //  iOS comes from the Core Data pipeline in DeviceWithState.swift, so the shared check is disabled.
    val updateTagResolver = UpdateTagResolver { _, _ -> null }

    return DeviceConnectionManager(
        store = database.createDeviceRepository(),
        connectionFactory = factory,
        connectivity = connectivity ?: IosNetworkConnectivityManager(scope),
        updateTagResolver = updateTagResolver,
        repositoryIdResolver = RepositoryIdResolver { database.repositoryDao().getOrCreateRepositoryId(it) },
        coroutineScope = scope,
        backgroundDispatcher = backgroundDispatcher,
        ioDispatcher = ioDispatcher,
        config = config,
    )
}
