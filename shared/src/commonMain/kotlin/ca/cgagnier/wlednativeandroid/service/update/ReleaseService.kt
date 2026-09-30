package ca.cgagnier.wlednativeandroid.service.update

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.UpdateSourceRegistry
import ca.cgagnier.wlednativeandroid.model.Version
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.isOtaEnabled
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import co.touchlab.kermit.Logger
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

private const val TAG = "updateService"
private val logger = Logger.withTag(TAG)
const val DEFAULT_REPO = ca.cgagnier.wlednativeandroid.model.Repository.DEFAULT_OWNER_REPO

/**
 * Extracts repository from device info using a three-tier fallback strategy:
 * 1. First: Use the repo field if available (format: "owner/name") - added in WLED 0.15.2
 * 2. Second: Use UpdateSourceRegistry based on brand pattern matching
 * 3. Third: Default to "wled/WLED"
 */
fun getRepositoryFromInfo(info: Info): String {
    // First priority: Use original repo, if supplied and not 'unknown'
    val repository = info.repository
    if (!repository.isNullOrBlank() && !repository.equals("unknown", ignoreCase = true)) {
        return repository
    }

    // Second priority: Use brand-based registry lookup
    val source = UpdateSourceRegistry.getSource(info)
    return source?.let { "${it.githubOwner}/${it.githubRepo}" } ?: DEFAULT_REPO
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
        logger.w { "Invalid repo format: $repository, using default" }
        val defaultParts = DEFAULT_REPO.split("/")
        return Pair(defaultParts[0], defaultParts[1])
    }
}

class ReleaseService(
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val repositoryDao: RepositoryDao,
) {

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
    suspend fun getNewerReleaseTag(deviceInfo: Info, branch: Branch, ignoreVersion: String): String? {
        if (!isDeviceEligibleForUpdate(deviceInfo)) {
            return null
        }

        val repositoryStr = getRepositoryFromInfo(deviceInfo)
        val repository = repositoryDao.getRepositoryByOwnerAndRepo(repositoryStr)

        val latestVersion = repository?.let {
            getLatestVersionWithAssets(it.id, branch)
        }

        return latestVersion?.version?.tagName?.takeIf {
            shouldOfferUpdate(deviceInfo, it, ignoreVersion, branch)
        }
    }

    private fun isDeviceEligibleForUpdate(deviceInfo: Info): Boolean =
        !deviceInfo.version.isNullOrEmpty() && deviceInfo.isOtaEnabled

    private fun shouldOfferUpdate(
        deviceInfo: Info,
        latestTagName: String,
        ignoreVersion: String,
        branch: Branch,
    ): Boolean {
        val currentVersion = deviceInfo.version ?: return false

        // Don't offer ignored versions or already-installed versions
        if (latestTagName == ignoreVersion || latestTagName == currentVersion) {
            return false
        }

        val betaSuffixes = listOf("-a", "-b", "-rc")
        val isDeviceOnBeta = betaSuffixes.any {
            currentVersion.contains(it, ignoreCase = true)
        }

        logger.d {
            "Checking update eligibility for device ${deviceInfo.ipAddress}: " +
                "current $currentVersion -> latest $latestTagName"
        }

        // Check branch transition first, then SemVer comparison
        // If we're on a beta branch but looking for a stable branch, always offer to "update" to
        // the stable branch.
        return isBranchTransition(branch, isDeviceOnBeta) ||
            isNewerVersion(currentVersion, latestTagName)
    }

    // Same if we are on a stable branch but looking for a beta branch, we should offer to
    // "update" to the latest beta branch, even if its older.
    private fun isBranchTransition(branch: Branch, isDeviceOnBeta: Boolean): Boolean =
        (branch == Branch.STABLE && isDeviceOnBeta) || (branch == Branch.BETA && !isDeviceOnBeta)

    private fun isNewerVersion(currentVersion: String, latestTagName: String): Boolean {
        val latestSemver = latestTagName.toVersionOrNull(strict = false)
        val currentSemver = currentVersion.toVersionOrNull(strict = false)

        return if (latestSemver != null && currentSemver != null) {
            latestSemver > currentSemver
        } else {
            logger.i {
                "Non-SemVer version detected (current: '$currentVersion', latest: '$latestTagName'), " +
                    "offering update as it differs from current."
            }
            true
        }
    }

    private suspend fun getLatestVersionWithAssets(repositoryId: Long, branch: Branch): VersionWithAssets? {
        if (branch == Branch.BETA) {
            return versionWithAssetsRepository.getLatestBetaVersionWithAssets(repositoryId)
        }

        return versionWithAssetsRepository.getLatestStableVersionWithAssets(repositoryId)
    }

    /**
     * Refreshes versions from multiple repositories.
     * Gets a list of unique repositories, then fetches releases for each.
     */
    suspend fun refreshVersions(githubApi: GithubApi, repositories: Set<String>) = withContext(Dispatchers.IO) {
        for (repository in repositories) {
            val (repoOwner, repoName) = splitRepository(repository)
            logger.i { "Fetching releases from $repository" }
            githubApi.getAllReleases(repoOwner, repoName).onFailure { exception ->
                logger.w(exception) { "Failed to refresh versions from $repository" }
            }.onSuccess { releases ->
                if (releases.isEmpty()) {
                    logger.w { "GitHub returned 0 releases for $repository." }
                } else {
                    val repoModel = Repository(
                        name = repoName,
                        ownerAndRepo = repository,
                        description = "",
                        htmlUrl = "https://github.com/$repository",
                    )

                    // We need to return pair of version with its assets so the repository
                    // can map the new autogenerated IDs
                    val versionAndAssetsMap = releases.associate { release ->
                        createVersion(release, 0L) to createAssetsForVersion(release, 0L)
                    }
                    logger.i { "Updating ${versionAndAssetsMap.size} versions and assets for $repository" }
                    versionWithAssetsRepository.updateRepository(repoModel, versionAndAssetsMap)
                }
            }
        }
    }

    private fun createVersion(version: Release, repositoryId: Long): Version = Version(
        repositoryId = repositoryId,
        tagName = sanitizeTagName(version.tagName),
        name = version.name,
        description = version.body,
        isPrerelease = version.prerelease,
        publishedDate = version.publishedAt,
        htmlUrl = version.htmlUrl,
    )

    private fun createAssetsForVersion(version: Release, versionId: Long): List<Asset> {
        val assetsModels = mutableListOf<Asset>()
        for (asset in version.assets) {
            assetsModels.add(
                Asset(
                    versionId = versionId,
                    name = asset.name,
                    size = asset.size,
                    downloadUrl = asset.browserDownloadUrl,
                    assetId = asset.id,
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
