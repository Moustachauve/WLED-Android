package ca.cgagnier.wlednativeandroid.service

import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.repository.createDeviceRepository
import ca.cgagnier.wlednativeandroid.repository.getOrCreateRepositoryId
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import ca.cgagnier.wlednativeandroid.service.api.defaultJson
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.update.RepositoryIdResolver
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceConnectionFactory
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob

/**
 * Creates the production or test [DeviceConnectionManager] for iOS.
 */
@Suppress("LongParameterList")
fun DeviceConnectionManager.Companion.create(
    database: DevicesDatabase,
    connectionFactory: DeviceConnectionFactory? = null,
    connectivity: NetworkConnectivityManager? = null,
    coroutineScope: CoroutineScope? = null,
    httpClient: HttpClient? = null,
    backgroundDispatcher: CoroutineDispatcher = Dispatchers.Default,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    config: DeviceConnectionManagerConfig = DeviceConnectionManagerConfig(),
): DeviceConnectionManager {
    val store = database.createDeviceRepository()
    val scope = coroutineScope ?: CoroutineScope(SupervisorJob() + backgroundDispatcher)
    val client = httpClient ?: DeviceApiFactory.createHttpClient()
    val factory = connectionFactory ?: WebsocketClientFactory(
        httpClient = client,
        json = defaultJson,
        dispatcher = ioDispatcher,
    )
    val networkConnectivity = connectivity ?: IosNetworkConnectivityManager(scope)
    val repositoryIdResolver = RepositoryIdResolver { database.repositoryDao().getOrCreateRepositoryId(it) }
    val versionRepo = VersionWithAssetsRepository(
        database = database,
        repositoryDao = database.repositoryDao(),
        versionDao = database.versionDao(),
        assetDao = database.assetDao(),
    )
    val releaseService = ReleaseService(
        versionWithAssetsRepository = versionRepo,
        repositoryDao = database.repositoryDao(),
    )
    val updateTagResolver = DeviceUpdateManager(releaseService)

    return DeviceConnectionManager(
        store = store,
        connectionFactory = factory,
        connectivity = networkConnectivity,
        updateTagResolver = updateTagResolver,
        repositoryIdResolver = repositoryIdResolver,
        coroutineScope = scope,
        backgroundDispatcher = backgroundDispatcher,
        ioDispatcher = ioDispatcher,
        config = config,
    )
}

/**
 * Creates a [DeviceConnectionManager] for iOS with optional custom connection factory and config.
 */
fun DeviceConnectionManager.Companion.create(
    database: DevicesDatabase,
    connectionFactory: DeviceConnectionFactory?,
    config: DeviceConnectionManagerConfig,
): DeviceConnectionManager = create(
    database = database,
    connectionFactory = connectionFactory,
    connectivity = null,
    coroutineScope = null,
    httpClient = null,
    backgroundDispatcher = Dispatchers.Default,
    ioDispatcher = Dispatchers.IO,
    config = config,
)

/**
 * Creates a [DeviceConnectionManager] for iOS with optional custom connection factory.
 */
fun DeviceConnectionManager.Companion.create(
    database: DevicesDatabase,
    connectionFactory: DeviceConnectionFactory?,
): DeviceConnectionManager = create(
    database = database,
    connectionFactory = connectionFactory,
    config = DeviceConnectionManagerConfig(),
)

/**
 * Creates a [DeviceConnectionManager] for iOS with custom config.
 */
fun DeviceConnectionManager.Companion.create(
    database: DevicesDatabase,
    config: DeviceConnectionManagerConfig,
): DeviceConnectionManager = create(
    database = database,
    connectionFactory = null,
    config = config,
)

/**
 * Creates a [DeviceConnectionManager] for iOS with default platform dependencies.
 */
fun DeviceConnectionManager.Companion.create(database: DevicesDatabase): DeviceConnectionManager = create(
    database = database,
    connectionFactory = null,
    connectivity = null,
    coroutineScope = null,
    httpClient = null,
    backgroundDispatcher = Dispatchers.Default,
    ioDispatcher = Dispatchers.IO,
    config = DeviceConnectionManagerConfig(),
)
