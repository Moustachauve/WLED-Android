package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.IOException

interface GithubApiEndpoints {
    suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release>

    fun downloadReleaseBinary(repoOwner: String, repoName: String, assetId: Int, targetFile: File): Flow<DownloadState>
}

class KtorGithubApiEndpoints(private val httpClient: HttpClient, private val baseUrl: String = GITHUB_BASE_URL) :
    GithubApiEndpoints {

    override suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release> {
        val url = "$baseUrl/repos/$repoOwner/$repoName/releases"
        return httpClient.get(url).body()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun downloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
        targetFile: File,
    ): Flow<DownloadState> = flow {
        try {
            emit(DownloadState.Downloading(0))
            val url = "$baseUrl/repos/$repoOwner/$repoName/releases/assets/$assetId"
            httpClient.prepareGet(url) {
                header(HttpHeaders.Accept, "application/octet-stream")
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw IOException("Download failed: HTTP ${response.status.value} ${response.status.description}")
                }
                val channel = response.bodyAsChannel()
                val totalBytes = response.contentLength() ?: -1L
                emitAll(channel.saveFile(targetFile, totalBytes))
            }
        } catch (e: Exception) {
            targetFile.delete()
            emit(DownloadState.Failed(e))
        }
    }.flowOn(Dispatchers.IO)

    @Suppress("TooGenericExceptionCaught")
    private fun ByteReadChannel.saveFile(destinationFile: File, totalBytes: Long): Flow<DownloadState> = flow {
        try {
            destinationFile.outputStream().use { outputStream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var progressBytes = 0L
                while (!isClosedForRead) {
                    val bytesRead = readAvailable(buffer, 0, buffer.size)
                    if (bytesRead <= 0) break
                    outputStream.write(buffer, 0, bytesRead)
                    progressBytes += bytesRead
                    if (totalBytes > 0) {
                        emit(DownloadState.Downloading((progressBytes * PERCENT_MAX / totalBytes).toInt()))
                    }
                }
            }
            emit(DownloadState.Finished)
        } catch (e: Exception) {
            destinationFile.delete()
            emit(DownloadState.Failed(e))
        }
    }.flowOn(Dispatchers.IO).distinctUntilChanged()

    companion object {
        const val GITHUB_BASE_URL = "https://api.github.com"
        private const val PERCENT_MAX = 100
    }
}
