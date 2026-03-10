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
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApiEndpoints
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import com.google.common.truth.Truth.assertThat
import okhttp3.OkHttpClient
import org.junit.Test
import retrofit2.Retrofit

class DeviceUpdateServiceTest {

    private fun makeVersion(tagName: String) = Version(
        tagName = tagName,
        name = "WLED $tagName",
        description = "",
        isPrerelease = false,
        publishedDate = "2024-01-01T00:00:00Z",
        htmlUrl = "https://github.com/",
    )

    private fun makeAsset(versionTagName: String, assetName: String) = Asset(
        versionTagName = versionTagName,
        name = assetName,
        size = 1024L,
        downloadUrl = "https://example.com/$assetName",
        assetId = 0,
    )

    private fun makeDeviceUpdateService(
        targetVersionTag: String,
        release: String?,
        availableAssets: List<Asset>,
    ): DeviceUpdateService {
        val device = Device(macAddress = "AA:BB:CC:DD:EE:FF", address = "192.168.1.1")
        val deviceWithState = DeviceWithState(device)
        if (release != null) {
            deviceWithState.stateInfo.value = DeviceStateInfo(
                state = State(),
                info = Info(
                    leds = Leds(),
                    wifi = Wifi(),
                    name = "Test Device",
                    release = release,
                ),
            )
        }
        val versionWithAssets =
            VersionWithAssets(version = makeVersion(targetVersionTag), assets = availableAssets)
        val deviceApiFactory = DeviceApiFactory(OkHttpClient())
        val githubApiEndpoints = Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .build()
            .create(GithubApiEndpoints::class.java)
        val githubApi = GithubApi(githubApiEndpoints)

        return DeviceUpdateService(
            device = deviceWithState,
            versionWithAssets = versionWithAssets,
            cacheDir = java.nio.file.Files.createTempDirectory("wled_test").toFile(),
            deviceApiFactory = deviceApiFactory,
            githubApi = githubApi,
        )
    }

    // --- Override applied for target >= 0.16.0 ---

    @Test
    fun determineAsset_esp32V4Release_targetVersion0160_usesEsp32Asset() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.16.0",
            release = "ESP32_V4",
            availableAssets = listOf(makeAsset("0.16.0", "WLED_0.16.0_ESP32.bin")),
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.16.0_ESP32.bin")
    }

    @Test
    fun determineAsset_esp32V4Release_targetVersion0161_usesEsp32Asset() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.16.1",
            release = "ESP32_V4",
            availableAssets = listOf(makeAsset("0.16.1", "WLED_0.16.1_ESP32.bin")),
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.16.1_ESP32.bin")
    }

    @Test
    fun determineAsset_esp32Release_targetVersion0160_passesThrough() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.16.0",
            release = "ESP32",
            availableAssets = listOf(makeAsset("0.16.0", "WLED_0.16.0_ESP32.bin")),
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.16.0_ESP32.bin")
    }

    @Test
    fun determineAsset_unknownRelease_targetVersion0160_passesThrough() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.16.0",
            release = "ESP32_S3",
            availableAssets = listOf(makeAsset("0.16.0", "WLED_0.16.0_ESP32_S3.bin")),
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.16.0_ESP32_S3.bin")
    }

    // --- No override within 0.15.x ---

    @Test
    fun determineAsset_esp32V4Release_targetVersion0150_usesEsp32V4Asset() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.15.0",
            release = "ESP32_V4",
            availableAssets = listOf(makeAsset("0.15.0", "WLED_0.15.0_ESP32_V4.bin")),
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.15.0_ESP32_V4.bin")
    }

    @Test
    fun determineAsset_esp32V4Release_targetVersion0151_usesEsp32V4Asset() {
        val service = makeDeviceUpdateService(
            targetVersionTag = "0.15.1",
            release = "ESP32_V4",
            availableAssets = listOf(makeAsset("0.15.1", "WLED_0.15.1_ESP32_V4.bin")),
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.15.1_ESP32_V4.bin")
    }
}
