package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.plugins.websocket.WebSockets
import platform.Foundation.NSURLRequestReloadIgnoringLocalAndRemoteCacheData
import platform.Foundation.NSURLRequestReturnCacheDataElseLoad
import platform.Foundation.NSURLSessionConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class DeviceApiFactoryIosTest {

    /** Applies the client's session configuration to a fresh `NSURLSessionConfiguration`. */
    private fun resolvedSessionConfiguration(
        configureSession: (NSURLSessionConfiguration) -> Unit = {},
    ): NSURLSessionConfiguration {
        val client = DeviceApiFactory.createHttpClient(configureSession = configureSession)
        val engineConfig = assertIs<DarwinClientEngineConfig>(client.engine.config)
        return NSURLSessionConfiguration.defaultSessionConfiguration.apply(engineConfig.sessionConfig)
    }

    @Test
    fun createHttpClientUsesDarwinEngine() {
        val client = DeviceApiFactory.createHttpClient()

        assertIs<DarwinClientEngineConfig>(client.engine.config)
    }

    @Test
    fun createHttpClientDisablesResponseCaching() {
        assertEquals(
            NSURLRequestReloadIgnoringLocalAndRemoteCacheData,
            resolvedSessionConfiguration().requestCachePolicy,
        )
    }

    @Test
    fun createHttpClientAppliesCustomSessionConfiguration() {
        val configuration = resolvedSessionConfiguration { it.HTTPMaximumConnectionsPerHost = 3 }

        assertEquals(3, configuration.HTTPMaximumConnectionsPerHost)
    }

    @Test
    fun customSessionConfigurationCanOverrideCachePolicy() {
        val configuration = resolvedSessionConfiguration {
            it.requestCachePolicy = NSURLRequestReturnCacheDataElseLoad
        }

        assertEquals(NSURLRequestReturnCacheDataElseLoad, configuration.requestCachePolicy)
    }

    @Test
    fun createHttpClientInstallsHttpTimeout() {
        assertNotNull(DeviceApiFactory.createHttpClient().pluginOrNull(HttpTimeout))
    }

    @Test
    fun createHttpClientInstallsDeviceApiDefaults() {
        val client = DeviceApiFactory.createHttpClient()

        assertNotNull(client.pluginOrNull(ContentNegotiation))
        assertNotNull(client.pluginOrNull(WebSockets))
    }
}
