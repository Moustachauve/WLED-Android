package ca.cgagnier.wlednativeandroid.service.api.github

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubApi @Inject constructor(private val apiEndpoints: GithubApiEndpoints) {

    suspend fun getAllReleases(repoOwner: String, repoName: String): Result<List<Release>> {
        Log.d(TAG, "retrieving latest releases from $repoOwner/$repoName")
        return try {
            Result.success(apiEndpoints.getAllReleases(repoOwner, repoName))
        } catch (e: Exception) {
            Log.w(TAG, "Error retrieving releases from $repoOwner/$repoName: ${e.message}")
            Result.failure(e)
        }
    }

    fun downloadReleaseBinary(
        asset: Asset,
        repoOwner: String,
        repoName: String,
        targetFile: File,
    ): Flow<DownloadState> {
        Log.d(TAG, "downloading release binary asset ${asset.name} (id: ${asset.assetId})")
        return apiEndpoints.downloadReleaseBinary(repoOwner, repoName, asset.assetId, targetFile)
    }

    companion object {
        private const val TAG = "github-release"
    }
}
