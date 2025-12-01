package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.flow.Flow
import java.io.File

class DeviceUpdateService(
    val device: DeviceWithState,
    private val versionWithAssets: VersionWithAssets,
    private val cacheDir: File
) {
    private val supportedPlatforms = listOf(
        "esp01",
        "esp02",
        "esp32",
        "esp8266"
    )

    private var assetName: String = ""
    private var couldDetermineAsset: Boolean = false
    private lateinit var asset: Asset

    init {
        // Try to use the release variable, but fallback to the legacy platform method for
        // compatibility with WLED older than 0.15.0
        if (!determineAssetByRelease()) {
            determineAssetByPlatform()
        }
    }

    // Preferred method, only available since WLED 0.15.0
    private fun determineAssetByRelease(): Boolean {
        val release = device.stateInfo.value?.info?.release
        if (release.isNullOrEmpty()) {
            return false
        }

        val combined = "${versionWithAssets.version.tagName}_${release}"
        val versionWithRelease =
            if (combined.startsWith("v", ignoreCase = true)) combined.drop(1) else combined
        assetName = "WLED_${versionWithRelease}.bin"
        return findAsset(assetName)
    }

    // Legacy method for backwards compatibility with WLED older than 0.15.0
    private fun determineAssetByPlatform(): Boolean {
        val deviceInfo = device.stateInfo.value?.info
        if (deviceInfo == null || !supportedPlatforms.contains(deviceInfo.platformName)) {
            return false
        }

        // TODO: Add support for Ethernet devices. Support was never fully implemented.
        // val ethernetVariant = if (deviceInfo.isEthernet) "_Ethernet" else ""
        val combined =
            "${versionWithAssets.version.tagName}_${deviceInfo.platformName?.uppercase()}"
        val versionWithPlatform =
            if (combined.startsWith("v", ignoreCase = true)) combined.drop(1) else combined
        assetName = "WLED_${versionWithPlatform}.bin"
        return findAsset(assetName)
    }

    private fun findAsset(assetName: String): Boolean {
        for (asset in versionWithAssets.assets) {
            if (asset.name == assetName) {
                this.asset = asset
                couldDetermineAsset = true
                return true
            }
        }
        return false
    }

    /**
     * Get the name of the asset to download.
     */
    fun getAssetName(): String {
        return assetName
    }

    fun couldDetermineAsset(): Boolean {
        return couldDetermineAsset
    }

    fun isAssetFileCached(): Boolean {
        return getPathForAsset().exists()
    }

    suspend fun downloadBinary(): Flow<DownloadState> {
        if (!::asset.isInitialized) {
            throw Exception("Asset could not be determined for ${device.device.macAddress}.")
        }

        val githubApi = GithubApi(cacheDir)
        return githubApi.downloadReleaseBinary(asset, getPathForAsset())
    }

    fun getPathForAsset(): File {
        val cacheDirectory = File(cacheDir, versionWithAssets.version.tagName)
        cacheDirectory.mkdirs()
        return File(cacheDirectory, asset.name)
    }
}