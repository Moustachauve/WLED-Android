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
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceUpdateServiceTest {

    private data class AssetResolutionCase(
        val targetVersionTag: String,
        val release: String?,
        val platformName: String? = null,
        val expectedAssetName: String,
    )

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

    private fun newTempCacheDir(): Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "wled_update_test_${Random.nextLong(0, Long.MAX_VALUE)}"

    private fun makeDeviceUpdateService(
        targetVersionTag: String,
        release: String?,
        platformName: String? = null,
        availableAssets: List<Asset>,
        cacheDir: Path = newTempCacheDir(),
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
            cacheDir = cacheDir,
            deviceApiFactory = deviceApiFactory,
            githubApi = githubApi,
        )
    }

    private fun assertDeterminedAssets(cases: List<AssetResolutionCase>) {
        cases.forEach { case ->
            val service = makeDeviceUpdateService(
                targetVersionTag = case.targetVersionTag,
                release = case.release,
                platformName = case.platformName,
                availableAssets = listOf(makeAsset(1L, case.expectedAssetName)),
            )

            assertTrue(
                service.couldDetermineAsset(),
                "Expected asset for ${case.targetVersionTag} / ${case.release} / ${case.platformName}",
            )
            assertEquals(case.expectedAssetName, service.getAssetName())
        }
    }

    @Test
    fun determineAssetAppliesReleaseOverrideOn016AndNewer() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase("0.16.0", "ESP32_V4", expectedAssetName = "WLED_0.16.0_ESP32.bin"),
                AssetResolutionCase("0.16.1", "ESP32_V4", expectedAssetName = "WLED_0.16.1_ESP32.bin"),
                AssetResolutionCase("0.16.0", "esp32_v4", expectedAssetName = "WLED_0.16.0_ESP32.bin"),
                AssetResolutionCase("0.16.0-b2", "ESP32_V4", expectedAssetName = "WLED_0.16.0-b2_ESP32.bin"),
                AssetResolutionCase("0.16.0+build1", "ESP32_V4", expectedAssetName = "WLED_0.16.0+build1_ESP32.bin"),
            ),
        )
    }

    @Test
    fun determineAssetPassesThroughStandardReleaseNames() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase("0.16.0", "ESP32", expectedAssetName = "WLED_0.16.0_ESP32.bin"),
                AssetResolutionCase("0.16.0", "ESP32_S3", expectedAssetName = "WLED_0.16.0_ESP32_S3.bin"),
                AssetResolutionCase("0.16.0", "ESP8266", expectedAssetName = "WLED_0.16.0_ESP8266.bin"),
            ),
        )
    }

    @Test
    fun determineAssetPreservesEsp32V4Before016() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase("0.15.0", "ESP32_V4", expectedAssetName = "WLED_0.15.0_ESP32_V4.bin"),
                AssetResolutionCase("0.15.1", "ESP32_V4", expectedAssetName = "WLED_0.15.1_ESP32_V4.bin"),
                AssetResolutionCase("0.15.0-b5", "ESP32_V4", expectedAssetName = "WLED_0.15.0-b5_ESP32_V4.bin"),
            ),
        )
    }

    @Test
    fun determineAssetFallsBackToLegacyPlatformWhenReleaseIsMissing() {
        assertDeterminedAssets(
            listOf(
                AssetResolutionCase(
                    targetVersionTag = "0.14.4",
                    release = null,
                    platformName = "esp32",
                    expectedAssetName = "WLED_0.14.4_ESP32.bin",
                ),
            ),
        )
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
    fun isAssetFileCachedDoesNotCreateCacheDirectory() {
        val cacheDir = newTempCacheDir()
        try {
            val service = makeDeviceUpdateService(
                targetVersionTag = "0.15.0",
                release = "ESP32",
                availableAssets = listOf(makeAsset(1L, "WLED_0.15.0_ESP32.bin")),
                cacheDir = cacheDir,
            )

            assertFalse(service.isAssetFileCached())
            assertFalse(FileSystem.SYSTEM.exists(cacheDir))
        } finally {
            FileSystem.SYSTEM.deleteRecursively(cacheDir)
        }
    }

    @Test
    fun resolveReleaseOverrideFallsBackToRawReleaseForNonSemVerTag() {
        assertEquals(
            "ESP32_V4",
            DeviceUpdateService.resolveReleaseOverride("ESP32_V4", "invalid_version_string"),
        )
    }
}
