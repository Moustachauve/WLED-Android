package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import org.koin.core.context.stopKoin
import org.koin.test.KoinTest
import org.koin.test.get
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull

class KoinIosTest : KoinTest {

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun initKoin_loadsSharedAndIosPlatformModules() {
        initKoin()
        assertNotNull(get<DeviceRepository>())
        assertNotNull(get<UserPreferencesRepository>())
    }
}
