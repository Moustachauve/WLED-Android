package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.isOtaEnabled
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import com.vdurmont.semver4j.Semver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "updateService"

enum class UpdateSourceType {
    OFFICIAL_WLED, QUINLED, CUSTOM
}

data class UpdateSourceDefinition(
    val type: UpdateSourceType,
    val brandPattern: String,
    val githubOwner: String,
    val githubRepo: String
)

object UpdateSourceRegistry {
    val sources = listOf(
        UpdateSourceDefinition(
            type = UpdateSourceType.OFFICIAL_WLED,
            brandPattern = "WLED",
            githubOwner = "Aircoookie",
            githubRepo = "WLED"
        ), UpdateSourceDefinition(
            type = UpdateSourceType.QUINLED,
            brandPattern = "QuinLED",
            githubOwner = "intermittech",
            githubRepo = "QuinLED-Firmware"
        )
    )

    fun getSource(info: Info): UpdateSourceDefinition? {
        return sources.find {
            info.brand == it.brandPattern
        }
    }
}

class ReleaseService(private val versionWithAssetsRepository: VersionWithAssetsRepository) {

    /**
     * If a new version is available, returns the version tag of it.
     *
     * @param deviceInfo Latest information about the device
     * @param branch Which branch to check for the update
     * @param ignoreVersion You can specify a version tag to be ignored as a new version. If this is
     *      set and match with the newest version, no version will be returned
     * @return The newest version if it is newer than versionName and different than ignoreVersion,
     *      otherwise an empty string.
     */
    suspend fun getNewerReleaseTag(
        deviceInfo: Info,
        branch: Branch,
        ignoreVersion: String,
        updateSourceDefinition: UpdateSourceDefinition,
    ): String? {
        if (deviceInfo.version.isNullOrEmpty()) {
            return null
        }
        if (deviceInfo.brand != updateSourceDefinition.brandPattern) {
            return null
        }
        if (!deviceInfo.isOtaEnabled) {
            return null
        }

        // TODO: Modify this to use repositoryOwner and repositoryName
        val latestVersion = getLatestVersionWithAssets(branch) ?: return null
        val latestTagName = latestVersion.version.tagName

        if (latestTagName == ignoreVersion) {
            return null
        }

        // Don't offer to update to the already installed version
        if (latestTagName == deviceInfo.version) {
            return null
        }

        val betaSuffixes = listOf("-a", "-b", "-rc")
        Log.w(
            TAG, "Device ${deviceInfo.ipAddress}: ${deviceInfo.version} to $latestTagName"
        )
        if (branch == Branch.STABLE && betaSuffixes.any {
                deviceInfo.version.contains(it, ignoreCase = true)
            }) {
            // If we're on a beta branch but looking for a stable branch, always offer to "update" to
            // the stable branch.
            return latestTagName
        } else if (branch == Branch.BETA && betaSuffixes.none {
                deviceInfo.version.contains(it, ignoreCase = true)
            }) {
            // Same if we are on a stable branch but looking for a beta branch, we should offer to
            // "update" to the latest beta branch, even if its older.
            return latestTagName
        }

        try {
            // Attempt strict SemVer comparison
            val versionSemver = Semver(latestTagName, Semver.SemverType.LOOSE)

            // If the version is mathematically greater, return it
            if (versionSemver.isGreaterThan(deviceInfo.version)) {
                return latestTagName
            }
        } catch (e: Exception) {
            Log.i(TAG, "Non-SemVer version detected ($latestTagName), offering update as it differs from current.")
            return latestTagName
        }

        return null
    }

    private suspend fun getLatestVersionWithAssets(branch: Branch): VersionWithAssets? {
        if (branch == Branch.BETA) {
            return versionWithAssetsRepository.getLatestBetaVersionWithAssets()
        }

        return versionWithAssetsRepository.getLatestStableVersionWithAssets()
    }

    suspend fun refreshVersions(githubApi: GithubApi) = withContext(Dispatchers.IO) {
        githubApi.getAllReleases().onFailure { exception ->
            Log.w(TAG, "Failed to refresh versions from Github", exception)
            return@onFailure
        }.onSuccess { allVersions ->
            if (allVersions.isEmpty()) {
                Log.w(TAG, "GitHub returned 0 releases. Skipping DB update to preserve cache.")
                return@onSuccess
            }
            val (versions, assets) = withContext(Dispatchers.Default) {
                val v = allVersions.map { createVersion(it) }
                val a = allVersions.flatMap { createAssetsForVersion(it) }
                Pair(v, a)
            }

            Log.i(TAG, "Replacing DB with ${versions.size} versions and ${assets.size} assets")
            versionWithAssetsRepository.replaceAll(versions, assets)
        }
    }

    private fun createVersion(version: Release): Version {
        return Version(
            sanitizeTagName(version.tagName),
            version.name,
            version.body,
            version.prerelease,
            version.publishedAt,
            version.htmlUrl
        )
    }

    private fun createAssetsForVersion(version: Release): List<Asset> {
        val assetsModels = mutableListOf<Asset>()
        val sanitizedTagName = sanitizeTagName(version.tagName)
        for (asset in version.assets) {
            assetsModels.add(
                Asset(
                    sanitizedTagName,
                    asset.name,
                    asset.size,
                    asset.browserDownloadUrl,
                    asset.id,
                )
            )
        }
        return assetsModels
    }

    /**
     * Removes the leading 'v' from version tags (e.g., "v0.14.0" -> "0.14.0").
     * Leaves other tags (like "nightly") untouched.
     */
    private fun sanitizeTagName(tagName: String): String = tagName.removePrefix("v")
}