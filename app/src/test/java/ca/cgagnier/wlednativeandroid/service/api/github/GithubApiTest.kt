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
import org.junit.Assert.assertFalse
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

    // Real release structure from https://api.github.com/repos/wled/WLED/releases/tags/v16.0.1
    private val sampleReleasesJson = """
        [
            {
                "url": "https://api.github.com/repos/wled/WLED/releases/347085524",
                "assets_url": "https://api.github.com/repos/wled/WLED/releases/347085524/assets",
                "upload_url": "https://uploads.github.com/repos/wled/WLED/releases/347085524/assets{?name,label}",
                "html_url": "https://github.com/wled/WLED/releases/tag/v16.0.1",
                "id": 347085524,
                "node_id": "RE_kwDOBJbHAc4UsHDU",
                "tag_name": "v16.0.1",
                "target_commitish": "main",
                "name": "WLED Release 16.0.1",
                "draft": false,
                "prerelease": false,
                "created_at": "2026-06-30T17:15:37Z",
                "published_at": "2026-06-30T21:12:47Z",
                "tarball_url": "https://api.github.com/repos/wled/WLED/tarball/v16.0.1",
                "zipball_url": "https://api.github.com/repos/wled/WLED/zipball/v16.0.1",
                "body": "# WLED Version 16.0.1 Announcement",
                "author": {
                    "login": "github-actions[bot]",
                    "id": 41898282,
                    "node_id": "MDM6Qm90NDE4OTgyODI=",
                    "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                    "gravatar_id": "",
                    "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                    "html_url": "https://github.com/apps/github-actions",
                    "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                    "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                    "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                    "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                    "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                    "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                    "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                    "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                    "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                    "type": "Bot",
                    "site_admin": false
                },
                "assets": [
                    {
                        "url": "https://api.github.com/repos/wled/WLED/releases/assets/462495760",
                        "id": 462495760,
                        "node_id": "RA_kwDOBJbHAc4bkSAQ",
                        "name": "WLED_16.0.1_ESP32.bin",
                        "label": "",
                        "uploader": {
                            "login": "github-actions[bot]",
                            "id": 41898282,
                            "node_id": "MDM6Qm90NDE4OTgyODI=",
                            "avatar_url": "https://avatars.githubusercontent.com/in/15368?v=4",
                            "gravatar_id": "",
                            "url": "https://api.github.com/users/github-actions%5Bbot%5D",
                            "html_url": "https://github.com/apps/github-actions",
                            "followers_url": "https://api.github.com/users/github-actions%5Bbot%5D/followers",
                            "following_url": "https://api.github.com/users/github-actions%5Bbot%5D/following{/other_user}",
                            "gists_url": "https://api.github.com/users/github-actions%5Bbot%5D/gists{/gist_id}",
                            "starred_url": "https://api.github.com/users/github-actions%5Bbot%5D/starred{/owner}{/repo}",
                            "subscriptions_url": "https://api.github.com/users/github-actions%5Bbot%5D/subscriptions",
                            "organizations_url": "https://api.github.com/users/github-actions%5Bbot%5D/orgs",
                            "repos_url": "https://api.github.com/users/github-actions%5Bbot%5D/repos",
                            "events_url": "https://api.github.com/users/github-actions%5Bbot%5D/events{/privacy}",
                            "received_events_url": "https://api.github.com/users/github-actions%5Bbot%5D/received_events",
                            "type": "Bot",
                            "site_admin": false
                        },
                        "content_type": "application/octet-stream",
                        "state": "uploaded",
                        "size": 1729440,
                        "download_count": 5218,
                        "created_at": "2026-06-30T20:28:04Z",
                        "updated_at": "2026-06-30T20:28:04Z",
                        "browser_download_url": "https://github.com/wled/WLED/releases/download/v16.0.1/WLED_16.0.1_ESP32.bin"
                    }
                ]
            }
        ]
    """.trimIndent()

    @Test
    fun `getAllReleases returns successful release list`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/repos/wled/WLED/releases", request.url.encodedPath)
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

        val result = githubApi.getAllReleases("wled", "WLED")

        assertTrue(result.isSuccess)
        val releases = result.getOrNull()
        assertEquals(1, releases?.size)
        assertEquals("v16.0.1", releases?.first()?.tagName)
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

        val result = githubApi.getAllReleases("wled", "WLED")

        assertTrue(result.isFailure)
    }

    @Test
    fun `downloadReleaseBinary streams bytes and reports progress`() = runTest {
        val binaryData = ByteArray(1024) { (it % 256).toByte() }
        val mockEngine = MockEngine { request ->
            assertEquals("/repos/wled/WLED/releases/assets/462495760", request.url.encodedPath)
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
            name = "WLED_16.0.1_ESP32.bin",
            size = 1729440L,
            downloadUrl = "https://github.com/wled/WLED/releases/download/v16.0.1/WLED_16.0.1_ESP32.bin",
            assetId = 462495760,
        )

        val states = githubApi.downloadReleaseBinary(
            asset = asset,
            repoOwner = "wled",
            repoName = "WLED",
            targetFile = targetFile,
        ).toList()

        assertTrue(states.any { it is DownloadState.Downloading })
        assertTrue(states.last() is DownloadState.Finished)
        assertEquals(1024L, targetFile.length())
    }

    @Test
    fun `downloadReleaseBinary fails and cleans up file on HTTP error`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "404 Not Found",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val httpClient = HttpClient(mockEngine)
        val endpoints = KtorGithubApiEndpoints(httpClient)
        val githubApi = GithubApi(endpoints)

        val targetFile = File.createTempFile("github_test_download_fail", ".bin")

        val asset = Asset(
            versionId = 1L,
            name = "WLED_16.0.1_ESP32.bin",
            size = 1729440L,
            downloadUrl = "https://github.com/wled/WLED/releases/download/v16.0.1/WLED_16.0.1_ESP32.bin",
            assetId = 462495760,
        )

        val states = githubApi.downloadReleaseBinary(
            asset = asset,
            repoOwner = "wled",
            repoName = "WLED",
            targetFile = targetFile,
        ).toList()

        assertTrue(states.last() is DownloadState.Failed)
        assertFalse(targetFile.exists())
    }
}
