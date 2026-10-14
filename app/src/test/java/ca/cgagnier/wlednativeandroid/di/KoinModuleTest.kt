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
import org.junit.jupiter.api.io.TempDir
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import java.io.File

class KoinModuleTest : KoinTest {

    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        val mockContext = mockk<Context>(relaxed = true)
        every { mockContext.cacheDir } returns tempDir
        every { mockContext.filesDir } returns tempDir
        every { mockContext.applicationContext } returns mockContext
        every { mockContext.getSystemService(ConnectivityManager::class.java) } returns mockk(relaxed = true)

        startKoin {
            androidContext(mockContext)
            modules(appModule)
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun resolveAllViewModels() {
        assertNotNull(get<MainViewModel>())
        assertNotNull(get<SettingsViewModel>())
        assertNotNull(get<ChangelogViewModel>())
        assertNotNull(get<ThemeViewModel>())
        assertNotNull(get<UpdateInstallingViewModel>())
        assertNotNull(get<DeviceEditViewModel>())
        assertNotNull(get<DeviceListDetailViewModel>())
        assertNotNull(get<DeviceWebsocketListViewModel>())
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
