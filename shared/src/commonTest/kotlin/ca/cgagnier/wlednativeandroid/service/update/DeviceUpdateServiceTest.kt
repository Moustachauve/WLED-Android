package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.github.KtorGithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import okio.Path.Companion.toPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceUpdateServiceTest {

    private fun makeVersion(tagName: String) = Version(
        tagName = tagName,
        name = "WLED $tagName",
        description = "",
        isPrerelease = tagName.contains("-"),
        publishedDate = "2024-01-01T00:00:00Z",
        htmlUrl = "https://github.com/wled/WLED",
        repositoryId = 1L,
    )

    private fun makeAsset(versionId: Long, assetName: String) = Asset(
        versionId = versionId,
        name = assetName,
        size = 1024L,
        downloadUrl = "https://example.com/$assetName",
        assetId = 0,
    )

    private fun makeDeviceUpdateService(
        targetVersionTag: String,
        release: String?,
        platformName: String? = null,
        availableAssets: List<Asset>,
    ): DeviceUpdateService {
        val device = Device(macAddress = "AA:BB:CC:DD:EE:FF", address = "192.168.1.1")
        val stateInfo = if (release != null || platformName != null) {
            DeviceStateInfo(
                state = State(),
                info = Info(
                    leds = Leds(),
                    wifi = Wifi(),
                    name = "Test Device",
                    release = release,
                    platformName = platformName,
                ),
            )
        } else {
            null
        }
        val deviceWithState = DeviceWithState(device, stateInfo = stateInfo)
        val versionWithAssets =
            VersionWithAssets(version = makeVersion(targetVersionTag), assets = availableAssets)
        val httpClient = HttpClient(MockEngine { respond("", HttpStatusCode.OK) })
        val deviceApiFactory = DeviceApiFactory(httpClient)
        val githubApi = GithubApi(KtorGithubApiEndpoints(httpClient))

        return DeviceUpdateService(
            device = deviceWithState,
            versionWithAssets = versionWithAssets,
            cacheDir = "/tmp/wled_test".toPath(),
            deviceApiFactory = deviceApiFactory,
            githubApi = githubApi,
        )
    }

    @Test
    fun determineAssetMapsEsp32V4ToEsp32OnVersion0160AndLater() {
        val cases = listOf(
            Triple("0.16.0", "ESP32_V4", "WLED_0.16.0_ESP32.bin"),
            Triple("0.16.1", "ESP32_V4", "WLED_0.16.1_ESP32.bin"),
            Triple("0.16.0", "esp32_v4", "WLED_0.16.0_ESP32.bin"),
            Triple("0.16.0-b2", "ESP32_V4", "WLED_0.16.0-b2_ESP32.bin"),
        )
        cases.forEach { (targetTag, release, expectedAsset) ->
            val service = makeDeviceUpdateService(
                targetVersionTag = targetTag,
                release = release,
                availableAssets = listOf(makeAsset(1L, expectedAsset)),
            )

            assertTrue(service.couldDetermineAsset(), "Expected asset for $targetTag / $release")
            assertEquals(expectedAsset, service.getAssetName())
        }
    }

    @Test
    fun determineAssetPassesThroughStandardAndUnknownReleasesOnVersion0160() {
        val cases = listOf(
            "ESP32" to "WLED_0.16.0_ESP32.bin",
            "ESP32_S3" to "WLED_0.16.0_ESP32_S3.bin",
            "ESP8266" to "WLED_0.16.0_ESP8266.bin",
        )
        cases.forEach { (release, expectedAsset) ->
            val service = makeDeviceUpdateService(
                targetVersionTag = "0.16.0",
                release = release,
                availableAssets = listOf(makeAsset(1L, expectedAsset)),
            )

            assertTrue(service.couldDetermineAsset(), "Expected asset for $release")
            assertEquals(expectedAsset, service.getAssetName())
        }
    }

    @Test
    fun determineAssetPreservesEsp32V4OnVersion015x() {
        val cases = listOf(
            "0.15.0" to "WLED_0.15.0_ESP32_V4.bin",
            "0.15.1" to "WLED_0.15.1_ESP32_V4.bin",
            "0.15.0-b5" to "WLED_0.15.0-b5_ESP32_V4.bin",
        )
        cases.forEach { (targetTag, expectedAsset) ->
            val service = makeDeviceUpdateService(
                targetVersionTag = targetTag,
                release = "ESP32_V4",
                availableAssets = listOf(makeAsset(1L, expectedAsset)),
            )

            assertTrue(service.couldDetermineAsset(), "Expected asset for $targetTag")
            assertEquals(expectedAsset, service.getAssetName())
        }
    }

    @Test
    fun determineAssetFallsBackToPlatformWhenReleaseIsMissing() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.14.4",
            release = null,
            platformName = "esp32",
            availableAssets = listOf(makeAsset(1L, "WLED_0.14.4_ESP32.bin")),
        )

        assertTrue(service.couldDetermineAsset())
        assertEquals("WLED_0.14.4_ESP32.bin", service.getAssetName())
    }

    @Test
    fun determineAssetDoesNotFallBackToGenericPlatformWhenReleaseIsKnown() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.15.0",
            release = "ESP32_Ethernet",
            platformName = "esp32",
            availableAssets = listOf(makeAsset(1L, "WLED_0.15.0_ESP32.bin")),
        )

        assertFalse(service.couldDetermineAsset())
        assertEquals("WLED_0.15.0_ESP32_Ethernet.bin", service.getAssetName())
    }

    @Test
    fun resolveReleaseOverrideFallsBackToRawReleaseForNonSemVerTag() {
        assertEquals(
            "ESP32_V4",
            DeviceUpdateService.resolveReleaseOverride("ESP32_V4", "invalid_version_string"),
        )
    }
}
