package ca.cgagnier.wlednativeandroid.di

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.DevicesDatabase
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.repository.createInMemoryDatabase
import ca.cgagnier.wlednativeandroid.repository.createUserPreferencesDataStore
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import okio.FileSystem
import okio.Path
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinIosTest : KoinTest {

    private lateinit var tempPath: Path

    @BeforeTest
    fun setUp() {
        tempPath = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "test_user_prefs_${Random.nextLong()}.json"
        startKoin {
            modules(
                iosPlatformModule,
                module {
                    single<DevicesDatabase> { DevicesDatabase.createInMemoryDatabase() }
                    single<DataStore<UserPreferences>> {
                        createUserPreferencesDataStore(producePath = { tempPath })
                    }
                },
            )
        }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        FileSystem.SYSTEM.delete(tempPath, mustExist = false)
    }

    @Test
    fun resolveAllIosDependencies() {
        assertNotNull(get<DeviceRepository>())
        assertNotNull(get<UserPreferencesRepository>())
        assertNotNull(get<DeviceConnectionManager>())
        assertNotNull(get<DeviceUpdateManager>())
        assertNotNull(get<GithubApi>())
        assertNotNull(get<DeviceApiFactory>())
        assertNotNull(get<WebsocketClientFactory>())
        assertNotNull(get<HttpClient>())
        assertNotNull(get<CoroutineScope>())
        assertNotNull(KoinHelper.deviceRepository)
        assertNotNull(KoinHelper.repositoryDao)
        assertNotNull(KoinHelper.versionWithAssetsRepository)
        assertNotNull(KoinHelper.userPreferencesRepository)
        assertNotNull(KoinHelper.deviceConnectionManager)
        assertNotNull(KoinHelper.releaseService)
        assertNotNull(KoinHelper.deviceUpdateManager)
        assertNotNull(KoinHelper.githubApi)
        assertNotNull(KoinHelper.deviceApiFactory)
    }
}
