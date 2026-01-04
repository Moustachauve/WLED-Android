package ca.cgagnier.wlednativeandroid.service.api.github

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.ResponseBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GithubApi @Inject constructor(private val apiEndpoints: GithubApiEndpoints) {

    suspend fun getAllReleases(): Result<List<Release>> {
        Log.d(TAG, "retrieving latest release")
        return try {
            Result.success(apiEndpoints.getAllReleases(REPO_OWNER, REPO_NAME))
        } catch (e: Exception) {
            Log.w(TAG, "Error retrieving releases: ${e.message}")
            Result.failure(e)
        }
    }

    fun downloadReleaseBinary(
        asset: Asset, targetFile: File
    ): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0))
            val responseBody =
                apiEndpoints.downloadReleaseBinary(REPO_OWNER, REPO_NAME, asset.assetId)
            emitAll(responseBody.saveFile(targetFile))
        } catch (e: Exception) {
            emit(DownloadState.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    private fun ResponseBody.saveFile(destinationFile: File): Flow<DownloadState> {
        return flow {
            emit(DownloadState.Downloading(0))

            try {
                byteStream().use { inputStream ->
                    destinationFile.outputStream().use { outputStream ->
                        val totalBytes = contentLength()
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var progressBytes = 0L
                        var bytes = inputStream.read(buffer)
                        while (bytes >= 0) {
                            outputStream.write(buffer, 0, bytes)
                            progressBytes += bytes
                            bytes = inputStream.read(buffer)
                            emit(DownloadState.Downloading(((progressBytes * 100) / totalBytes).toInt()))
                        }
                    }
                }
                emit(DownloadState.Finished)
            } catch (e: Exception) {
                emit(DownloadState.Failed(e))
            }
        }.flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    companion object {
        private const val TAG = "github-release"
        const val REPO_OWNER = "Aircoookie"
        const val REPO_NAME = "WLED"
    }
}