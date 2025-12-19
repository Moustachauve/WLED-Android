package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import com.vdurmont.semver4j.Semver

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
        ),
        UpdateSourceDefinition(
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

        // The options bitmask at 0x01 being 0 means OTA is disabled on the device.
        if (deviceInfo.options?.and(0x01) == 0) {
            return null
        }

        // TODO: Modify this to use repositoryOwner and repositoryName
        val latestVersion = getLatestVersionWithAssets(branch) ?: return null
        if (latestVersion.version.tagName == ignoreVersion) {
            return null
        }

        // Make sure we have a version that isn't prefixed with "v" to be able to properly compare
        // it to Device's version.
        val untaggedUpdateVersion = if (latestVersion.version.tagName.startsWith(
                'v', ignoreCase = true
            )
        ) latestVersion.version.tagName.drop(1) else latestVersion.version.tagName

        // Don't offer to update to the already installed version
        if (untaggedUpdateVersion == deviceInfo.version) {
            return null
        }

        val betaSuffixes = listOf("-a", "-b", "-rc")
        Log.w(
            TAG, "Device ${deviceInfo.ipAddress}: ${deviceInfo.version} to $untaggedUpdateVersion"
        )
        if (branch == Branch.STABLE && betaSuffixes.any {
                deviceInfo.version.contains(
                    it, ignoreCase = true
                )
            }) {
            // If we're on a beta branch but looking for a stable branch, always offer to "update" to
            // the stable branch.
            return latestVersion.version.tagName
        } else if (branch == Branch.BETA && betaSuffixes.none {
                deviceInfo.version.contains(
                    it, ignoreCase = true
                )
            }) {
            // Same if we are on a stable branch but looking for a beta branch, we should offer to
            // "update" to the latest beta branch, even if its older.
            return latestVersion.version.tagName
        }

        try {
            return if (Semver(untaggedUpdateVersion, Semver.SemverType.LOOSE).isGreaterThan(
                    deviceInfo.version
                )
            ) {
                latestVersion.version.tagName
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in getNewerReleaseTag: " + e.message, e)
        }

        return null
    }

    private suspend fun getLatestVersionWithAssets(branch: Branch): VersionWithAssets? {
        if (branch == Branch.BETA) {
            return versionWithAssetsRepository.getLatestBetaVersionWithAssets()
        }

        return versionWithAssetsRepository.getLatestStableVersionWithAssets()
    }

    suspend fun refreshVersions(githubApi: GithubApi) {
        val allVersionsResult = githubApi.getAllReleases()

        allVersionsResult.onFailure { exception ->
            Log.w(TAG, "Failed to refresh versions from Github", exception)
            return
        }

        allVersionsResult.onSuccess { allVersions ->
            versionWithAssetsRepository.removeAll()

            val versionModels = mutableListOf<Version>()
            val assetsModels = mutableListOf<Asset>()
            for (version in allVersions) {
                versionModels.add(createVersion(version))
                assetsModels.addAll(createAssetsForVersion(version))
            }

            Log.i(
                TAG,
                "Inserting " + versionModels.count() + " versions with " + assetsModels.count() + " assets"
            )
            versionWithAssetsRepository.insertMany(versionModels, assetsModels)
        }
    }

    private fun createVersion(version: Release): Version {
        return Version(
            version.tagName,
            version.name,
            version.body,
            version.prerelease,
            version.publishedAt,
            version.htmlUrl
        )
    }

    private fun createAssetsForVersion(version: Release): List<Asset> {
        val assetsModels = mutableListOf<Asset>()
        for (asset in version.assets) {
            assetsModels.add(
                Asset(
                    version.tagName,
                    asset.name,
                    asset.size,
                    asset.browserDownloadUrl,
                    asset.id,
                )
            )
        }
        return assetsModels
    }
}