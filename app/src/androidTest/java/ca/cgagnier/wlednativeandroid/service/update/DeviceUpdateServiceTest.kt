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

    private val version = Version(
        tagName = "v0.15.0",
        name = "WLED 0.15.0",
        description = "",
        isPrerelease = false,
        publishedDate = "2024-01-01T00:00:00Z",
        htmlUrl = "https://github.com/"
    )

    private fun makeAsset(name: String) = Asset(
        versionTagName = version.tagName,
        name = name,
        size = 1024L,
        downloadUrl = "https://example.com/$name",
        assetId = 0
    )

    private fun makeDeviceUpdateService(
        release: String?,
        availableAssets: List<Asset>
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
                    release = release
                )
            )
        }
        val versionWithAssets = VersionWithAssets(version = version, assets = availableAssets)
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
            githubApi = githubApi
        )
    }

    @Test
    fun determineAsset_esp32V4Release_usesEsp32Asset() {
        val esp32Asset = makeAsset("WLED_0.15.0_ESP32.bin")
        val service = makeDeviceUpdateService(
            release = "ESP32_V4",
            availableAssets = listOf(esp32Asset)
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.15.0_ESP32.bin")
    }

    @Test
    fun determineAsset_esp32Release_usesEsp32Asset() {
        val esp32Asset = makeAsset("WLED_0.15.0_ESP32.bin")
        val service = makeDeviceUpdateService(
            release = "ESP32",
            availableAssets = listOf(esp32Asset)
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.15.0_ESP32.bin")
    }

    @Test
    fun determineAsset_unknownRelease_passesThrough() {
        val unknownAsset = makeAsset("WLED_0.15.0_ESP32_S3.bin")
        val service = makeDeviceUpdateService(
            release = "ESP32_S3",
            availableAssets = listOf(unknownAsset)
        )

        assertThat(service.couldDetermineAsset()).isTrue()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.15.0_ESP32_S3.bin")
    }

    @Test
    fun determineAsset_esp32V4Release_withNoMatchingEsp32Asset_couldNotDetermineAsset() {
        // When the asset list only has the old ESP32_V4 binary and not the ESP32 binary,
        // determination should fail because ESP32_V4 is remapped to ESP32.
        val oldAsset = makeAsset("WLED_0.15.0_ESP32_V4.bin")
        val service = makeDeviceUpdateService(
            release = "ESP32_V4",
            availableAssets = listOf(oldAsset)
        )

        assertThat(service.couldDetermineAsset()).isFalse()
        assertThat(service.getAssetName()).isEqualTo("WLED_0.15.0_ESP32.bin")
    }
}
