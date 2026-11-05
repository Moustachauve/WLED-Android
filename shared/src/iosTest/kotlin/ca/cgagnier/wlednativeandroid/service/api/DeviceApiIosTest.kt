package ca.cgagnier.wlednativeandroid.service.api

import io.ktor.client.engine.darwin.DarwinClientEngineConfig
import platform.Foundation.NSURLRequestReloadIgnoringLocalAndRemoteCacheData
import platform.Foundation.NSURLSessionConfiguration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DeviceApiIosTest {

    /** Applies the client's session configuration to a fresh `NSURLSessionConfiguration`. */
    private fun resolvedSessionConfiguration(
        configureSession: (NSURLSessionConfiguration) -> Unit = {},
    ): NSURLSessionConfiguration =
        DeviceApiFactory.createHttpClient(configureSession = configureSession).use { client ->
            val engineConfig = assertIs<DarwinClientEngineConfig>(client.engine.config)
            NSURLSessionConfiguration.defaultSessionConfiguration.apply(engineConfig.sessionConfig)
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
}
