package ca.cgagnier.wlednativeandroid.service.api.github

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.net.UnknownHostException


class GithubApi(private val okHttpClient: OkHttpClient) {
    fun getAllReleases(): List<Release>? {
        Log.d(TAG, "retrieving latest release")
        try {
            val api = getApi()
            val release = api.getAllReleases(REPO_OWNER, REPO_NAME)
            val execute = release.execute()
            return execute.body()
        } catch (e: UnknownHostException) {
            Log.w(TAG, e.toString())
        } catch (e: Exception) {
            Log.e(TAG, e.toString())
        }
        return null
    }

    suspend fun downloadReleaseBinary(
        asset: Asset,
        targetFile: File
    ): Flow<DownloadState> {
        val api = getApi()
        return api.downloadReleaseBinary(REPO_OWNER, REPO_NAME, asset.assetId)
            .saveFile(targetFile)
    }

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
        }
            .flowOn(Dispatchers.IO).distinctUntilChanged()
    }

    private fun getApi(): GithubApiEndpoints {
        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create())
            .client(okHttpClient)
            .build()
            .create(GithubApiEndpoints::class.java)
    }

    companion object {
        private const val TAG = "github-release"
        const val BASE_URL = "https://api.github.com"
        const val REPO_OWNER = "Aircoookie"
        const val REPO_NAME = "WLED"
    }
}