package ca.cgagnier.wlednativeandroid.di

import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManagerConfig
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.create
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientFactory
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class KoinIosTest {

    @Test
    fun resolveAllIosDependencies() {
        val container = KoinContainer.inMemory()
        try {
            assertNotNull(container.database)
            assertNotNull(container.deviceRepository)
            assertNotNull(container.repositoryDao)
            assertNotNull(container.versionWithAssetsRepository)
            assertNotNull(container.userPreferencesRepository)
            assertNotNull(container.deviceConnectionManager)
            assertNotNull(container.deviceFirstContactService)
            assertNotNull(container.addressValidator)
            assertNotNull(container.releaseService)
            assertNotNull(container.deviceUpdateManager)
            assertNotNull(container.githubApi)
            assertNotNull(container.deviceApiFactory)
            assertNotNull(container.koin.get<DeviceConnectionManagerConfig>())
            assertNotNull(container.koin.get<WebsocketClientFactory>())
            assertNotNull(container.koin.get<HttpClient>())
            assertNotNull(container.koin.get<CoroutineScope>())
        } finally {
            container.close()
        }
    }

    @Test
    fun inMemoryContainersAreIsolatedAndShareSingletonsInternally() {
        val containerA = KoinContainer.inMemory()
        val containerB = KoinContainer.inMemory()
        try {
            assertSame(containerA.deviceRepository, containerA.deviceRepository)
            assertSame(containerA.deviceConnectionManager, containerA.deviceConnectionManager)
            assertNotSame(containerA.database, containerB.database)
            assertNotSame(containerA.deviceRepository, containerB.deviceRepository)
            assertNotSame(containerA.deviceConnectionManager, containerB.deviceConnectionManager)
        } finally {
            containerA.close()
            containerB.close()
        }
    }

    @Test
    fun inMemoryContainerAppliesCustomDeviceApiFactoryOverride() {
        val customFactory = DeviceApiFactory.create { }
        val container = KoinContainer.inMemory(deviceApiFactory = customFactory)
        try {
            assertSame(customFactory, container.deviceApiFactory)
        } finally {
            container.close()
        }
    }
}
