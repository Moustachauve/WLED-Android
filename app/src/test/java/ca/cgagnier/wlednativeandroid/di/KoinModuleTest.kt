package ca.cgagnier.wlednativeandroid.di

import android.content.Context
import android.net.ConnectivityManager
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
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
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import java.io.File

class KoinModuleTest : KoinTest {

    @BeforeEach
    fun setUp() {
        val mockContext = mockk<Context>(relaxed = true)
        val tempDir = File(System.getProperty("java.io.tmpdir"), "koin-test-${System.currentTimeMillis()}").apply {
            mkdirs()
        }
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.filesDir } returns tempDir
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(ConnectivityManager::class.java) } returns mockk(relaxed = true)

        startKoin {
            androidContext(mockContext)
            modules(sharedModules + appModule)
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun resolveMainViewModel() {
        assertNotNull(get<MainViewModel>())
    }

    @Test
    fun resolveSettingsViewModel() {
        assertNotNull(get<SettingsViewModel>())
    }

    @Test
    fun resolveChangelogViewModel() {
        assertNotNull(get<ChangelogViewModel>())
    }

    @Test
    fun resolveThemeViewModel() {
        assertNotNull(get<ThemeViewModel>())
    }

    @Test
    fun resolveUpdateInstallingViewModel() {
        assertNotNull(get<UpdateInstallingViewModel>())
    }

    @Test
    fun resolveDeviceEditViewModel() {
        assertNotNull(get<DeviceEditViewModel>())
    }

    @Test
    fun resolveDeviceListDetailViewModel() {
        assertNotNull(get<DeviceListDetailViewModel>())
    }

    @Test
    fun resolveDeviceWebsocketListViewModel() {
        assertNotNull(get<DeviceWebsocketListViewModel>())
    }

    @Test
    fun resolveDeviceAddViewModel() {
        assertNotNull(get<DeviceAddViewModel>())
    }

    @Test
    fun resolveAppServices() {
        assertNotNull(get<WledWidgetManager>())
        assertNotNull(get<DeviceFirstContactService>())
        assertNotNull(get<NetworkConnectivityManager>())
        assertNotNull(get<DeviceDiscovery>())
    }
}
