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
import javax.inject.Inject

private const val TAG = "updateService"
const val DEFAULT_REPO = "wled/WLED"

enum class UpdateSourceType {
    OFFICIAL_WLED,
    QUINLED,
    CUSTOM,
    MOONMODULES,
}

data class UpdateSourceDefinition(
    val type: UpdateSourceType,
    val brandPattern: String,
    val githubOwner: String,
    val githubRepo: String,
    val product: String? = null
)

object UpdateSourceRegistry {
    val sources = listOf(
        UpdateSourceDefinition(
            type = UpdateSourceType.OFFICIAL_WLED,
            brandPattern = "WLED",
            githubOwner = "wled",
            githubRepo = "WLED"
        ), UpdateSourceDefinition(
            type = UpdateSourceType.QUINLED,
            brandPattern = "QuinLED",
            githubOwner = "intermittech",
            githubRepo = "QuinLED-Firmware"
        ),
        UpdateSourceDefinition(
            type = UpdateSourceType.MOONMODULES,
            brandPattern = "WLED",
            product = "MoonModules",
            githubOwner = "MoonModules",
            githubRepo = "WLED-MM"
        ),
    )

    fun getSource(info: Info): UpdateSourceDefinition? {
        val brandMatches = sources.filter { it.brandPattern == info.brand }
        return brandMatches.find { it.product == info.product }
            ?: brandMatches.find { it.product == null }
    }
}

/**
 * Extracts repository from device info using a three-tier fallback strategy:
 * 1. First: Use the repo field if available (format: "owner/name") - added in WLED 0.15.2
 * 2. Second: Use UpdateSourceRegistry based on brand pattern matching
 * 3. Third: Default to "wled/WLED"
 */
fun getRepositoryFromInfo(info: Info): String {

    // First priority: Use original repo, if supplied
    if (!info.repo.isNullOrBlank()) {
        return info.repo
    }

    // Second priority: Use brand-based registry lookup
    val source = UpdateSourceRegistry.getSource(info)
    if (source != null) {
        return "${source.githubOwner}/${source.githubRepo}"
    }
    
    // Final fallback: Default repository
    return DEFAULT_REPO
}

/**
 * Splits a repository string (e.g., "owner/name") into owner and name parts for API calls.
 * Returns a pair of (owner, name). Defaults to ("wled", "WLED") if format is invalid.t
 */
fun splitRepository(repository: String): Pair<String, String> {
    val parts = repository.split("/")
    if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
        return Pair(parts[0], parts[1])
    } else {
        Log.w(TAG, "Invalid repo format: $repository, using default")
        val defaultParts = DEFAULT_REPO.split("/")
        return Pair(defaultParts[0], defaultParts[1])
    }
}

class ReleaseService @Inject constructor(private val versionWithAssetsRepository: VersionWithAssetsRepository) {

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
    ): String? {
        if (deviceInfo.version.isNullOrEmpty()) {
            return null
        }
        if (!deviceInfo.isOtaEnabled) {
            return null
        }

        val repository = getRepositoryFromInfo(deviceInfo)
        val latestVersion = getLatestVersionWithAssets(repository, branch) ?: return null
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
            TAG,
            "Device ${deviceInfo.ipAddress}: ${deviceInfo.version} to $latestTagName",
        )
        if (branch == Branch.STABLE && betaSuffixes.any {
                deviceInfo.version.contains(it, ignoreCase = true)
            }
        ) {
            // If we're on a beta branch but looking for a stable branch, always offer to "update" to
            // the stable branch.
            return latestTagName
        } else if (branch == Branch.BETA && betaSuffixes.none {
                deviceInfo.version.contains(it, ignoreCase = true)
            }
        ) {
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

    private suspend fun getLatestVersionWithAssets(
        repository: String,
        branch: Branch
    ): VersionWithAssets? {
        if (branch == Branch.BETA) {
            return versionWithAssetsRepository.getLatestBetaVersionWithAssets(repository)
        }

        return versionWithAssetsRepository.getLatestStableVersionWithAssets(repository)
    }

    /**
     * Refreshes versions from multiple repositories.
     * Gets a list of unique repositories, then fetches releases for each.
     */
    suspend fun refreshVersions(githubApi: GithubApi, repositories: Set<String>) = withContext(Dispatchers.IO) {
        for (repository in repositories) {
            val (repoOwner, repoName) = splitRepository(repository)
            Log.i(TAG, "Fetching releases from $repository")
            githubApi.getAllReleases(repoOwner, repoName).onFailure { exception ->
                Log.w(TAG, "Failed to refresh versions from $repository", exception)
            }.onSuccess { releases ->
                if (releases.isEmpty()) {
                    Log.w(TAG, "GitHub returned 0 releases for $repository.")
                } else {
                    val versions = releases.map { createVersion(it, repository) }
                    val assets = releases.flatMap { createAssetsForVersion(it, repository) }
                    Log.i(TAG, "Updating ${versions.size} versions and ${assets.size} assets for $repository")
                    versionWithAssetsRepository.updateRepository(repository, versions, assets)
                }
            }
        }
    }

    private fun createVersion(version: Release, repository: String): Version {
        return Version(
            sanitizeTagName(version.tagName),
            repository,
            version.name,
            version.body,
            version.prerelease,
            version.publishedAt,
            version.htmlUrl
        )
    }

    private fun createAssetsForVersion(version: Release, repository: String): List<Asset> {
        val assetsModels = mutableListOf<Asset>()
        val sanitizedTagName = sanitizeTagName(version.tagName)
        for (asset in version.assets) {
            assetsModels.add(
                Asset(
                    sanitizedTagName,
                    repository,
                    asset.name,
                    asset.size,
                    asset.browserDownloadUrl,
                    asset.id,
                ),
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
