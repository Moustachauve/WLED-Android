package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.domain.usecase.SaveDeviceStateUseCase
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import okio.Path.Companion.toPath
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinIosTest : KoinTest {

    @BeforeTest
    fun setUp() {
        val tempPath = (NSTemporaryDirectory() + "test_user_prefs_${NSUUID().UUIDString}.json").toPath()
        initKoinForTest(
            preferencesPathProducer = { tempPath },
        )
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun resolveDeviceRepository() {
        assertNotNull(get<DeviceRepository>())
    }

    @Test
    fun resolveUserPreferencesRepository() {
        assertNotNull(get<UserPreferencesRepository>())
    }

    @Test
    fun resolveSaveDeviceStateUseCase() {
        assertNotNull(get<SaveDeviceStateUseCase>())
    }

    @Test
    fun resolveDeviceUpdateManager() {
        assertNotNull(get<DeviceUpdateManager>())
    }

    @Test
    fun resolveGithubApi() {
        assertNotNull(get<GithubApi>())
    }

    @Test
    fun resolveDeviceApiFactory() {
        assertNotNull(get<DeviceApiFactory>())
    }

    @Test
    fun resolveWebsocketClientFactory() {
        assertNotNull(get<WebsocketClientFactory>())
    }

    @Test
    fun resolveHttpClient() {
        assertNotNull(get<HttpClient>())
    }

    @Test
    fun resolveCoroutineScope() {
        assertNotNull(get<CoroutineScope>())
    }

    @Test
    fun resolveKoinHelper() {
        assertNotNull(KoinHelper.deviceRepository)
        assertNotNull(KoinHelper.userPreferencesRepository)
    }
}
