package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import ca.cgagnier.wlednativeandroid.shared.ioDispatcher
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.buffer
import okio.use
import ca.cgagnier.wlednativeandroid.shared.fileSystem as defaultFileSystem

interface GithubApiEndpoints {
    suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release>

    fun downloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
        destinationPath: Path,
    ): Flow<DownloadState>
}

class KtorGithubApiEndpoints(
    private val httpClient: HttpClient,
    private val baseUrl: String = GITHUB_BASE_URL,
    private val fs: FileSystem = defaultFileSystem,
) : GithubApiEndpoints {

    override suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release> {
        val url = "$baseUrl/repos/$repoOwner/$repoName/releases"
        return httpClient.get(url).body()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun downloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
        destinationPath: Path,
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
                emitAll(channel.saveFile(destinationPath, totalBytes))
            }
        } catch (e: Exception) {
            try {
                fs.delete(destinationPath)
            } catch (_: Exception) {
            }
            emit(DownloadState.Failed(e))
        }
    }.flowOn(ioDispatcher)

    @Suppress("TooGenericExceptionCaught")
    private fun ByteReadChannel.saveFile(destinationPath: Path, totalBytes: Long): Flow<DownloadState> =
        flow<DownloadState> {
            try {
                destinationPath.parent?.let { parent ->
                    fs.createDirectories(parent)
                }
                fs.sink(destinationPath).buffer().use { sink ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var progressBytes = 0L
                    while (!isClosedForRead) {
                        val bytesRead = readAvailable(buffer, 0, buffer.size)
                        if (bytesRead <= 0) break
                        sink.write(buffer, 0, bytesRead)
                        progressBytes += bytesRead
                        if (totalBytes > 0) {
                            emit(DownloadState.Downloading((progressBytes * PERCENT_MAX / totalBytes).toInt()))
                        }
                    }
                }
                emit(DownloadState.Finished)
            } catch (e: Exception) {
                try {
                    fs.delete(destinationPath)
                } catch (_: Exception) {
                }
                emit(DownloadState.Failed(e))
            }
        }.flowOn(ioDispatcher).distinctUntilChanged()

    companion object {
        const val GITHUB_BASE_URL = "https://api.github.com"
        private const val PERCENT_MAX = 100
        private const val BUFFER_SIZE = 8192
    }
}
