package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import ca.cgagnier.wlednativeandroid.domain.ChangelogProvider
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler
import ca.cgagnier.wlednativeandroid.domain.usecase.ValidateAddress
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.USER_PREFERENCES_DATA_STORE_FILE_NAME
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.repository.getDatabase
import ca.cgagnier.wlednativeandroid.repository.migrations.LegacyProtoToKotlinxPreferencesMigration
import ca.cgagnier.wlednativeandroid.repository.migrations.UserPreferencesV0ToV1
import ca.cgagnier.wlednativeandroid.service.AndroidDeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.AndroidNetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.createHttpClient
import ca.cgagnier.wlednativeandroid.ui.ChangelogViewModel
import ca.cgagnier.wlednativeandroid.ui.MainViewModel
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetailViewModel
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAddViewModel
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit.DeviceEditViewModel
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateInstallingViewModel
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.SettingsViewModel
import ca.cgagnier.wlednativeandroid.ui.theme.ThemeViewModel
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import io.ktor.client.HttpClient
import okhttp3.Cache
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private const val CACHE_SIZE_BYTES = 20 * 1024 * 1024L // 20MB
private const val MAX_REQUESTS = 64
private const val MAX_REQUESTS_PER_HOST = 16

val appModule: Module = module {
    single<DevicesDatabase> {
        DevicesDatabase.getDatabase(androidContext())
    }

    single<DataStore<UserPreferences>> {
        val context = androidContext()
        val preferencesPath = context
            .dataStoreFile(USER_PREFERENCES_DATA_STORE_FILE_NAME)
            .absolutePath
            .toPath()
        createUserPreferencesDataStore(
            producePath = { preferencesPath },
            migrations = listOf(
                LegacyProtoToKotlinxPreferencesMigration(context),
                UserPreferencesV0ToV1(),
            ),
        )
    }

    single<OkHttpClient> {
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = MAX_REQUESTS
            maxRequestsPerHost = MAX_REQUESTS_PER_HOST
        }
        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(DeviceApiFactory.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DeviceApiFactory.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(DeviceApiFactory.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .pingInterval(DeviceApiFactory.DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .cache(Cache(androidContext().cacheDir, CACHE_SIZE_BYTES))
            .build()
    }

    single<HttpClient> {
        DeviceApiFactory.createHttpClient(get(), get())
    }

    single<NetworkConnectivityManager> {
        AndroidNetworkConnectivityManager(androidContext(), get())
    }

    single<DeviceDiscovery> {
        AndroidDeviceDiscovery(androidContext())
    }

    single { ValidateAddress() }
    single { DeepLinkHandler() }
    single { ChangelogProvider(androidContext()) }
    single { WledWidgetManager(get(), get()) }
    single { DeviceFirstContactService(get(), get(), get(), get(), androidContext()) }

    viewModel {
        MainViewModel(
            userPreferencesRepository = get(),
            releaseService = get(),
            githubApi = get(),
            deviceRepository = get(),
            repositoryDao = get(),
            deviceFirstContactService = get(),
            deepLinkHandler = get(),
        )
    }

    viewModel { SettingsViewModel(get()) }

    viewModel { ChangelogViewModel(get(), get()) }

    viewModel { ThemeViewModel(get()) }

    viewModel {
        UpdateInstallingViewModel(
            deviceRepository = get(),
            repositoryDao = get(),
            deviceApiFactory = get(),
            githubApi = get(),
        )
    }

    viewModel {
        DeviceEditViewModel(
            deviceRepository = get(),
            repositoryDao = get(),
            versionWithAssetsRepository = get(),
            githubApi = get(),
            releaseService = get(),
            widgetManager = get(),
            applicationContext = androidContext(),
        )
    }

    viewModel {
        DeviceListDetailViewModel(
            preferencesRepository = get(),
            networkManager = get(),
            deviceFirstContactService = get(),
            discoveryService = get(),
        )
    }

    viewModel {
        DeviceWebsocketListViewModel(
            userPreferencesRepository = get(),
            deviceRepository = get(),
            websocketClientFactory = get(),
            widgetManager = get(),
            saveDeviceStateUseCase = get(),
            deviceUpdateManager = get(),
            applicationContext = androidContext(),
            backgroundDispatcher = get(DEFAULT_DISPATCHER),
            ioDispatcher = get(IO_DISPATCHER),
        )
    }

    viewModel {
        DeviceAddViewModel(
            validateAddress = get(),
            deviceFirstContactService = get(),
        )
    }
}
