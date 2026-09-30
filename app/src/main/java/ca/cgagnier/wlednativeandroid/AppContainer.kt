package ca.cgagnier.wlednativeandroid

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import ca.cgagnier.wlednativeandroid.di.IoDispatcher
import ca.cgagnier.wlednativeandroid.domain.ChangelogProvider
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler
import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.domain.usecase.ValidateAddress
import ca.cgagnier.wlednativeandroid.repository.AssetDao
import ca.cgagnier.wlednativeandroid.repository.DeviceDao
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.USER_PREFERENCES_DATA_STORE_FILE_NAME
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.VersionDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.repository.getDatabase
import ca.cgagnier.wlednativeandroid.repository.migrations.LegacyProtoToKotlinxPreferencesMigration
import ca.cgagnier.wlednativeandroid.repository.migrations.UserPreferencesV0ToV1
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppContainer {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext appContext: Context): DevicesDatabase =
        DevicesDatabase.getDatabase(appContext)

    @Provides
    @Singleton
    fun provideDeviceDao(appDatabase: DevicesDatabase): DeviceDao = appDatabase.deviceDao()

    @Provides
    @Singleton
    fun provideVersionDao(appDatabase: DevicesDatabase): VersionDao = appDatabase.versionDao()

    @Provides
    @Singleton
    fun provideRepositoryDao(appDatabase: DevicesDatabase): RepositoryDao = appDatabase.repositoryDao()

    @Provides
    @Singleton
    fun provideAssetDao(appDatabase: DevicesDatabase): AssetDao = appDatabase.assetDao()

    @Provides
    @Singleton
    fun provideVersionWithAssetsRepository(
        appDatabase: DevicesDatabase,
        repositoryDao: RepositoryDao,
        versionDao: VersionDao,
        assetDao: AssetDao,
    ): VersionWithAssetsRepository = VersionWithAssetsRepository(appDatabase, repositoryDao, versionDao, assetDao)

    @Provides
    @Singleton
    fun providesReleaseService(
        versionWithAssetsRepository: VersionWithAssetsRepository,
        repositoryDao: RepositoryDao,
    ): ReleaseService = ReleaseService(versionWithAssetsRepository, repositoryDao)

    @Provides
    @Singleton
    fun provideUserPreferencesStore(@ApplicationContext appContext: Context): DataStore<UserPreferences> =
        createUserPreferencesDataStore(
            producePath = { appContext.dataStoreFile(USER_PREFERENCES_DATA_STORE_FILE_NAME).absolutePath.toPath() },
            migrations = listOf(
                LegacyProtoToKotlinxPreferencesMigration(appContext),
                UserPreferencesV0ToV1(),
            ),
        )

    @Provides
    @Singleton
    fun provideUserPreferencesRepository(dataStore: DataStore<UserPreferences>): UserPreferencesRepository =
        UserPreferencesRepository(dataStore)

    @Provides
    @Singleton
    fun providesCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun providesNetworkConnectivityManager(
        @ApplicationContext appContext: Context,
        coroutineScope: CoroutineScope,
    ): NetworkConnectivityManager = NetworkConnectivityManager(appContext, coroutineScope)

    @Provides
    @Singleton
    fun provideDeviceRepository(deviceDao: DeviceDao): DeviceRepository = DeviceRepository(deviceDao)

    @Provides
    @Singleton
    fun provideDeviceUpdateManager(releaseService: ReleaseService): DeviceUpdateManager =
        DeviceUpdateManager(releaseService)

    @Provides
    @Singleton
    fun provideSaveDeviceStateUseCase(
        deviceRepository: DeviceRepository,
        repositoryDao: RepositoryDao,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SaveDeviceStateUseCase = SaveDeviceStateUseCase(deviceRepository, repositoryDao, ioDispatcher)

    @Provides
    @Singleton
    fun provideValidateAddress(): ValidateAddress = ValidateAddress()

    @Provides
    @Singleton
    fun provideDeepLinkHandler(): DeepLinkHandler = DeepLinkHandler()

    @Provides
    @Singleton
    fun provideChangelogProvider(@ApplicationContext appContext: Context): ChangelogProvider =
        ChangelogProvider(appContext)
}
