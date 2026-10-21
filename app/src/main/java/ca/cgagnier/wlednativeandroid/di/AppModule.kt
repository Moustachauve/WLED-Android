package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import ca.cgagnier.wlednativeandroid.domain.ChangelogProvider
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler
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
import ca.cgagnier.wlednativeandroid.service.WidgetStateSync
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
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

private const val CACHE_SIZE_BYTES = 20 * 1024 * 1024L // 20MB
private const val MAX_REQUESTS = 64
private const val MAX_REQUESTS_PER_HOST = 16

val appModule: Module = module {
    includes(sharedModules)

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

    // Explicit constructors used because multiple constructors cause overload resolution ambiguity with singleOf
    single<NetworkConnectivityManager> {
        AndroidNetworkConnectivityManager(androidContext(), get())
    }

    single<DeviceDiscovery> {
        AndroidDeviceDiscovery(androidContext())
    }

    single<Lifecycle> {
        ProcessLifecycleOwner.get().lifecycle
    }

    singleOf(::DeepLinkHandler)
    singleOf(::ChangelogProvider)
    single {
        WledWidgetManager(get(), get())
    }
    single {
        val context = androidContext()
        val widgetManager: WledWidgetManager = get()
        DeviceFirstContactService(
            repositoryDao = get(),
            repository = get(),
            deviceApiFactory = get(),
            onDeviceAddressUpdated = { updatedDevice ->
                widgetManager.updateWidgetDeviceDetails(context, updatedDevice)
            },
        )
    }

    single {
        WidgetStateSync(
            context = androidContext(),
            connectionManager = get(),
            widgetManager = get(),
            coroutineScope = get(),
            ioDispatcher = get(IO_DISPATCHER),
        )
    }

    viewModelOf(::MainViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::ChangelogViewModel)
    viewModelOf(::ThemeViewModel)
    viewModelOf(::UpdateInstallingViewModel)
    viewModelOf(::DeviceEditViewModel)
    viewModel {
        DeviceListDetailViewModel(
            preferencesRepository = get(),
            networkManager = get(),
            deviceFirstContactService = get(),
            discoveryService = get(),
            ioDispatcher = get(IO_DISPATCHER),
            processLifecycle = getOrNull<Lifecycle>(),
        )
    }
    viewModelOf(::DeviceWebsocketListViewModel)
    viewModelOf(::DeviceAddViewModel)
}
