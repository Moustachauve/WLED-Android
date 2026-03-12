package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import com.vdurmont.semver4j.Semver
import kotlinx.coroutines.flow.Flow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File

private const val TAG = "DeviceUpdateService"

/**
 * The minimum target version from which [RELEASE_NAME_OVERRIDES] are applied.
 * Overrides only take effect when upgrading to this version or later.
 */
private val RELEASE_OVERRIDES_MIN_VERSION = Semver("0.16.0", Semver.SemverType.LOOSE)

/**
 * Maps deprecated or transitional release names to the release name that should be used
 * for OTA updates. This handles cases where the binary name changes between WLED releases.
 *
 * For example, devices running the ESP32_V4 tech-preview (WLED 0.15.x) must be updated
 * with the standard ESP32 binary when upgrading to 0.16.0 or later, as ESP32_V4 assets will
 * no longer be published in those releases. Within 0.15.x, ESP32_V4 assets still exist
 * and no remapping is needed.
 *
 * These overrides are only applied when the target version is [RELEASE_OVERRIDES_MIN_VERSION]
 * or greater.
 *
 * See: https://github.com/Moustachauve/WLED-Android/issues/129
 */
private val RELEASE_NAME_OVERRIDES = mapOf(
    "ESP32_V4" to "ESP32",
)

class DeviceUpdateService(
    val device: DeviceWithState,
    private val versionWithAssets: VersionWithAssets,
    private val cacheDir: File,
    private val deviceApiFactory: DeviceApiFactory,
    private val githubApi: GithubApi,
) {
    private val supportedPlatforms = listOf(
        "esp01",
        "esp02",
        "esp32",
        "esp8266",
    )

    private var assetName: String = ""
    private var couldDetermineAsset: Boolean = false
    private lateinit var asset: Asset

    init {
        // Try to use the release variable, but fallback to the legacy platform method for
        // compatibility with WLED older than 0.15.0
        if (hasReleaseName()) { // never swap to generic build if release name is known
            determineAssetByRelease()
        } else {
            determineAssetByPlatform()
        }
    }

    private fun hasReleaseName(): Boolean {
        val release = device.stateInfo.value?.info?.release
        return !release.isNullOrEmpty()
    }

    /**
     * Determine the asset to download based on the release variable.
     *
     * This is the preferred method. It is only available on WLED devices since 0.15.0.
     */
    private fun determineAssetByRelease(): Boolean {
        if (!hasReleaseName()) {
            return false
        }
        val rawRelease = device.stateInfo.value?.info!!.release!!

        val release = getReleaseOverride(rawRelease)
        val combined = "${versionWithAssets.version.tagName}_$release"
        val versionWithRelease =
            if (combined.startsWith("v", ignoreCase = true)) combined.drop(1) else combined
        assetName = "WLED_$versionWithRelease.bin"
        return findAsset(versionWithRelease)
    }

    private fun getReleaseOverride(rawRelease: String): String = try {
        val targetVersion = Semver(versionWithAssets.version.tagName, Semver.SemverType.LOOSE)
        if (!targetVersion.isLowerThan(RELEASE_OVERRIDES_MIN_VERSION)) {
            RELEASE_NAME_OVERRIDES.getOrDefault(rawRelease, rawRelease)
        } else {
            rawRelease
        }
    } catch (e: Exception) {
        Log.w(
            TAG,
            "Could not parse target version '${versionWithAssets.version.tagName}', skipping release name overrides",
            e,
        )
        rawRelease
    }

    /**
     * Determine the asset to download based on the platform.
     *
     * Legacy method for backwards compatibility with WLED devices older than 0.15.0
     */
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
        assetName = "WLED_$versionWithPlatform.bin"
        return findAsset(versionWithPlatform)
    }

    private fun findAsset(assetName: String): Boolean {
        for (asset in versionWithAssets.assets) {
            if (asset.name.endsWith("$assetName.bin")) {
                this.asset = asset
                this.assetName = asset.name
                couldDetermineAsset = true
                return true
            }
        }
        return false
    }

    /**
     * Get the name of the asset to download.
     */
    fun getAssetName(): String = assetName

    fun couldDetermineAsset(): Boolean = couldDetermineAsset

    fun isAssetFileCached(): Boolean = getPathForAsset().exists()

    suspend fun downloadBinary(): Flow<DownloadState> {
        if (!::asset.isInitialized) {
            throw Exception("Asset could not be determined for ${device.device.macAddress}.")
        }
        return githubApi.downloadReleaseBinary(asset, getPathForAsset())
    }

    fun getPathForAsset(): File {
        val cacheDirectory = File(cacheDir, versionWithAssets.version.tagName)
        cacheDirectory.mkdirs()
        return File(cacheDirectory, asset.name)
    }

    suspend fun sendSoftwareUpdateRequest(
        device: Device,
        binaryFile: File,
        callback: ((Response<ResponseBody>) -> Unit)? = null,
        errorCallback: ((Exception) -> Unit)? = null,
    ) {
        Log.d(TAG, "Installing software update: ${device.macAddress}")
        try {
            val reqFile = binaryFile.asRequestBody("application/octet-stream".toMediaTypeOrNull())
            // Longer TTL because updates can take a bit of time to fully install
            val response = deviceApiFactory.create(device, 120L).updateDevice(
                MultipartBody.Part.createFormData("file", "binary", reqFile),
            )
            callback?.invoke(response)
        } catch (e: Exception) {
            errorCallback?.invoke(e)
        }
    }
}
