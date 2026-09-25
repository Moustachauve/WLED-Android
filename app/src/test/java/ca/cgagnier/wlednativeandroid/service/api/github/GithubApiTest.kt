package ca.cgagnier.wlednativeandroid.service.api.github

import ca.cgagnier.wlednativeandroid.model.Asset
import ca.cgagnier.wlednativeandroid.service.api.DownloadState
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class GithubApiTest {

    private val testJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val sampleReleasesJson = """
        [
            {
                "url": "https://api.github.com/repos/Aircoookie/WLED/releases/1",
                "assets_url": "https://api.github.com/repos/Aircoookie/WLED/releases/1/assets",
                "upload_url": "https://uploads.github.com/repos/Aircoookie/WLED/releases/1/assets",
                "html_url": "https://github.com/Aircoookie/WLED/releases/tag/v0.14.0",
                "id": 1,
                "node_id": "MDc6UmVsZWFzZTE=",
                "tag_name": "v0.14.0",
                "target_commitish": "main",
                "name": "WLED 0.14.0",
                "draft": false,
                "prerelease": false,
                "created_at": "2024-01-01T00:00:00Z",
                "published_at": "2024-01-01T00:00:00Z",
                "tarball_url": "",
                "zipball_url": "",
                "body": "Release notes",
                "author": {
                    "login": "Aircoookie",
                    "id": 123,
                    "node_id": "MDQ6VXNlcjEyMw==",
                    "avatar_url": "",
                    "gravatar_id": "",
                    "url": "",
                    "html_url": "",
                    "followers_url": "",
                    "following_url": "",
                    "gists_url": "",
                    "starred_url": "",
                    "subscriptions_url": "",
                    "organizations_url": "",
                    "repos_url": "",
                    "events_url": "",
                    "received_events_url": "",
                    "type": "User",
                    "site_admin": false
                },
                "assets": [
                    {
                        "url": "https://api.github.com/repos/Aircoookie/WLED/releases/assets/12345",
                        "id": 12345,
                        "node_id": "MDE=",
                        "name": "WLED_0.14.0_ESP32.bin",
                        "label": null,
                        "uploader": {
                            "login": "Aircoookie",
                            "id": 123,
                            "node_id": "MDQ6VXNlcjEyMw==",
                            "avatar_url": "",
                            "gravatar_id": "",
                            "url": "",
                            "html_url": "",
                            "followers_url": "",
                            "following_url": "",
                            "gists_url": "",
                            "starred_url": "",
                            "subscriptions_url": "",
                            "organizations_url": "",
                            "repos_url": "",
                            "events_url": "",
                            "received_events_url": "",
                            "type": "User",
                            "site_admin": false
                        },
                        "content_type": "application/octet-stream",
                        "state": "uploaded",
                        "size": 1048576,
                        "download_count": 50,
                        "created_at": "2024-01-01T00:00:00Z",
                        "updated_at": "2024-01-01T00:00:00Z",
                        "browser_download_url": "https://github.com/downloads/WLED_0.14.0_ESP32.bin"
                    }
                ]
            }
        ]
    """.trimIndent()

    @Test
    fun `getAllReleases returns successful release list`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/repos/Aircoookie/WLED/releases", request.url.encodedPath)
            respond(
                content = sampleReleasesJson,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val endpoints = KtorGithubApiEndpoints(httpClient)
        val githubApi = GithubApi(endpoints)

        val result = githubApi.getAllReleases("Aircoookie", "WLED")

        assertTrue(result.isSuccess)
        val releases = result.getOrNull()
        assertEquals(1, releases?.size)
        assertEquals("v0.14.0", releases?.first()?.tagName)
        assertEquals(1, releases?.first()?.assets?.size)
        assertEquals("WLED_0.14.0_ESP32.bin", releases?.first()?.assets?.first()?.name)
    }

    @Test
    fun `getAllReleases handles network error gracefully`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Rate limited",
                status = HttpStatusCode.Forbidden,
            )
        }
        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(testJson)
            }
        }

        val endpoints = KtorGithubApiEndpoints(httpClient)
        val githubApi = GithubApi(endpoints)

        val result = githubApi.getAllReleases("Aircoookie", "WLED")

        assertTrue(result.isFailure)
    }

    @Test
    fun `downloadReleaseBinary streams bytes and reports progress`() = runTest {
        val binaryData = ByteArray(1024) { (it % 256).toByte() }
        val mockEngine = MockEngine { request ->
            assertEquals("/repos/Aircoookie/WLED/releases/assets/12345", request.url.encodedPath)
            respond(
                content = binaryData,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf("application/octet-stream"),
                    HttpHeaders.ContentLength to listOf("1024"),
                ),
            )
        }
        val httpClient = HttpClient(mockEngine)
        val endpoints = KtorGithubApiEndpoints(httpClient)
        val githubApi = GithubApi(endpoints)

        val targetFile = File.createTempFile("github_test_download", ".bin").apply {
            deleteOnExit()
        }

        val asset = Asset(
            versionId = 1L,
            name = "WLED_0.14.0_ESP32.bin",
            size = 1024L,
            downloadUrl = "https://example.com/download",
            assetId = 12345,
        )

        val states = githubApi.downloadReleaseBinary(
            asset = asset,
            repoOwner = "Aircoookie",
            repoName = "WLED",
            targetFile = targetFile,
        ).toList()

        assertTrue(states.any { it is DownloadState.Downloading })
        assertTrue(states.last() is DownloadState.Finished)
        assertEquals(1024L, targetFile.length())
    }
}
