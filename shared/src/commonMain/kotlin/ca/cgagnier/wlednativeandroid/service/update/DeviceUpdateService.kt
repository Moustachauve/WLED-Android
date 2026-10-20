package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.service.api.ApiResponse
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.api.updateDevice
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersion
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okio.FileSystem
import okio.Path
import ca.cgagnier.wlednativeandroid.shared.fileSystem as defaultFileSystem

private const val TAG = "DeviceUpdateService"
private val logger = Logger.withTag(TAG)
private const val UPDATE_TIMEOUT_SECONDS = 120L

/**
 * The minimum target version from which [RELEASE_NAME_OVERRIDES] are applied.
 * Overrides only take effect when upgrading to this version or later.
 */
private val RELEASE_OVERRIDES_MIN_VERSION = "0.16.0".toVersion(strict = false)

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

@Suppress("LongParameterList")
class DeviceUpdateService(
    val device: DeviceWithState,
    private val versionWithAssets: VersionWithAssets,
    private val cacheDir: Path,
    private val deviceApiFactory: DeviceApiFactory,
    private val githubApi: GithubApi,
    private val fs: FileSystem = defaultFileSystem,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
        val rawRelease = device.stateInfo?.info?.release
        if (!rawRelease.isNullOrEmpty()) { // never swap to generic build if release name is known
            determineAssetByRelease(rawRelease)
        } else {
            determineAssetByPlatform()
        }
    }

    /**
     * Determine the asset to download based on the release variable.
     *
     * This is the preferred method. It is only available on WLED devices since 0.15.0.
     */
    private fun determineAssetByRelease(rawRelease: String) {
        val release = resolveReleaseOverride(rawRelease, versionWithAssets.version.tagName)
        val versionWithRelease = formatVersionWithTarget(release)
        assetName = "WLED_$versionWithRelease.bin"
        findAsset(versionWithRelease)
    }

    /**
     * Determine the asset to download based on the platform.
     *
     * Legacy method for backwards compatibility with WLED devices older than 0.15.0
     */
    private fun determineAssetByPlatform() {
        val platformName = device.stateInfo?.info?.platformName
        if (platformName == null || !supportedPlatforms.contains(platformName)) {
            return
        }

        // TODO: Add support for Ethernet devices. Support was never fully implemented.
        // val ethernetVariant = if (deviceInfo.isEthernet) "_Ethernet" else ""
        val versionWithPlatform = formatVersionWithTarget(platformName.uppercase())
        assetName = "WLED_$versionWithPlatform.bin"
        findAsset(versionWithPlatform)
    }

    private fun formatVersionWithTarget(target: String): String {
        val combined = "${versionWithAssets.version.tagName}_$target"
        return if (combined.startsWith("v", ignoreCase = true)) combined.drop(1) else combined
    }

    private fun findAsset(assetName: String) {
        for (asset in versionWithAssets.assets) {
            if (asset.name.endsWith("$assetName.bin")) {
                this.asset = asset
                this.assetName = asset.name
                couldDetermineAsset = true
                return
            }
        }
    }

    /**
     * Get the name of the asset to download.
     */
    fun getAssetName(): String = assetName

    fun couldDetermineAsset(): Boolean = couldDetermineAsset

    fun isAssetFileCached(): Boolean = couldDetermineAsset && fs.exists(getPathForAsset())

    fun downloadBinary(repoOwner: String, repoName: String): Flow<DownloadState> {
        val targetPath = getPathForAsset()
        targetPath.parent?.let(fs::createDirectories)
        return githubApi.downloadReleaseBinary(asset, repoOwner, repoName, targetPath)
    }

    fun downloadBinary(repositoryDao: RepositoryDao): Flow<DownloadState> = flow {
        val repo = repositoryDao.getRepositoryById(device.device.repositoryId)
        val repoStr = repo?.ownerAndRepo ?: DEFAULT_REPO
        val (repoOwner, repoName) = splitRepository(repoStr)
        emitAll(downloadBinary(repoOwner, repoName))
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(DownloadState.Failed(error))
    }

    fun getPathForAsset(): Path {
        check(::asset.isInitialized) {
            "Asset could not be determined for ${device.device.macAddress}."
        }
        return cacheDir / versionWithAssets.version.tagName / asset.name
    }

    @Throws(Exception::class)
    suspend fun installUpdate(): ApiResponse<String> {
        logger.d { "Installing software update: ${device.device.macAddress}" }
        // Longer TTL because updates can take a bit of time to fully install
        return deviceApiFactory
            .create(device.device, UPDATE_TIMEOUT_SECONDS)
            .updateDevice(getPathForAsset(), fs, ioDispatcher)
    }

    companion object {
        fun resolveReleaseOverride(rawRelease: String, targetVersionTag: String): String {
            val targetVersion = targetVersionTag.toVersionOrNull(strict = false)
            if (targetVersion == null) {
                logger.w {
                    "Could not parse target version '$targetVersionTag', skipping release name overrides"
                }
                return rawRelease
            }
            val baseVersion = targetVersion.copy(preRelease = null, buildMetadata = null)
            return if (baseVersion >= RELEASE_OVERRIDES_MIN_VERSION) {
                RELEASE_NAME_OVERRIDES[rawRelease.uppercase()] ?: rawRelease
            } else {
                rawRelease
            }
        }
    }
}
