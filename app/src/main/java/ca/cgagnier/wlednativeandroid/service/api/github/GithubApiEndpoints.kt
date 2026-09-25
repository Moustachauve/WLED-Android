package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.githubapi.Release
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpStatement
import io.ktor.http.HttpHeaders

interface GithubApiEndpoints {
    suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release>

    suspend fun prepareDownloadReleaseBinary(repoOwner: String, repoName: String, assetId: Int): HttpStatement
}

class KtorGithubApiEndpoints(private val httpClient: HttpClient, private val baseUrl: String = GITHUB_BASE_URL) :
    GithubApiEndpoints {

    override suspend fun getAllReleases(repoOwner: String, repoName: String): List<Release> {
        val url = "$baseUrl/repos/$repoOwner/$repoName/releases"
        return httpClient.get(url).body()
    }

    override suspend fun prepareDownloadReleaseBinary(
        repoOwner: String,
        repoName: String,
        assetId: Int,
    ): HttpStatement {
        val url = "$baseUrl/repos/$repoOwner/$repoName/releases/assets/$assetId"
        return httpClient.prepareGet(url) {
            header(HttpHeaders.Accept, "application/octet-stream")
        }
    }

    companion object {
        const val GITHUB_BASE_URL = "https://api.github.com"
    }
}
