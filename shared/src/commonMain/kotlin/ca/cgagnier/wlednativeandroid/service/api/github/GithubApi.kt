package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import okio.Path

private const val TAG = "github-release"
private val logger = Logger.withTag(TAG)

class GithubApi(private val apiEndpoints: GithubApiEndpoints) {

    suspend fun getAllReleases(repoOwner: String, repoName: String): Result<List<Release>> {
        logger.d { "retrieving latest releases from $repoOwner/$repoName" }
        return try {
            Result.success(apiEndpoints.getAllReleases(repoOwner, repoName))
        } catch (e: Exception) {
            logger.w(e) { "Error retrieving releases from $repoOwner/$repoName" }
            Result.failure(e)
        }
    }

    fun downloadReleaseBinary(
        asset: Asset,
        repoOwner: String,
        repoName: String,
        targetPath: Path,
    ): Flow<DownloadState> {
        logger.d { "downloading release binary asset ${asset.name} (id: ${asset.assetId})" }
        return apiEndpoints.downloadReleaseBinary(repoOwner, repoName, asset.assetId, targetPath)
    }
}
