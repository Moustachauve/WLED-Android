package ca.cgagnier.wlednativeandroid.model.githubapi

import com.diffplug.selfie.Selfie.expectSelfie
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

private val SAMPLE_RELEASE_16_0_1_JSON = """
    {
        "url": "https://api.github.com/repos/wled/WLED/releases/347085524",
        "assets_url": "https://api.github.com/repos/wled/WLED/releases/347085524/assets",
        "upload_url": "https://uploads.github.com/repos/wled/WLED/releases/347085524/assets{?name,label}",
        "html_url": "https://github.com/wled/WLED/releases/tag/v16.0.1",
        "id": 347085524,
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
        "node_id": "RE_kwDOBJbHAc4UsHDU",
        "tag_name": "v16.0.1",
        "target_commitish": "main",
        "name": "WLED Release 16.0.1",
        "draft": false,
        "prerelease": false,
        "created_at": "2026-06-30T17:15:37Z",
        "published_at": "2026-06-30T21:12:47Z",
        "assets": [
            {
                "url": "https://api.github.com/repos/wled/WLED/releases/assets/462495760",
                "id": 462495760,
                "node_id": "RA_kwDOBJbHAc4bkSAQ",
                "name": "WLED_16.0.1_ESP8266_compat.bin",
                "label": null,
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
                "size": 934992,
                "download_count": 636,
                "created_at": "2026-06-30T20:28:04Z",
                "updated_at": "2026-06-30T20:28:04Z",
                "browser_download_url": "https://github.com/wled/WLED/releases/download/v16.0.1/WLED_16.0.1_ESP8266_compat.bin"
            }
        ],
        "tarball_url": "https://api.github.com/repos/wled/WLED/tarball/v16.0.1",
        "zipball_url": "https://api.github.com/repos/wled/WLED/zipball/v16.0.1",
        "body": "# WLED Version 16.0.1 Announcement",
        "reactions": {
            "url": "https://api.github.com/repos/wled/WLED/releases/347085524/reactions",
            "total_count": 25,
            "+1": 0,
            "-1": 0,
            "laugh": 0,
            "hooray": 14,
            "confused": 0,
            "heart": 8,
            "rocket": 3,
            "eyes": 0
        },
        "mentions_count": 10
    }
""".trimIndent()

private val SAMPLE_MINIMAL_RELEASE_JSON = """
    {
        "url": "https://api.github.com/repos/wled/WLED/releases/1",
        "assets_url": "https://api.github.com/repos/wled/WLED/releases/1/assets",
        "upload_url": "https://uploads.github.com/repos/wled/WLED/releases/1/assets",
        "html_url": "https://github.com/wled/WLED/releases/tag/v16.0.0-beta",
        "id": 1,
        "node_id": "MDc6UmVsZWFzZTE=",
        "tag_name": "v16.0.0-beta",
        "target_commitish": "dev",
        "name": "WLED 16.0.0 Beta",
        "draft": false,
        "prerelease": true,
        "created_at": "2026-05-01T12:00:00Z",
        "published_at": "2026-05-01T13:00:00Z",
        "tarball_url": "",
        "zipball_url": "",
        "body": "Beta release",
        "author": {
            "login": "tester",
            "id": 2,
            "node_id": "MDE=",
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
        "assets": []
    }
""".trimIndent()

class GithubApiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `test Release deserialization with real WLED 16_0_1 data`() {
        val release = json.decodeFromString<Release>(SAMPLE_RELEASE_16_0_1_JSON)
        expectSelfie(prettyJson.encodeToString(release)).toMatchDisk()
    }

    @Test
    fun `test Release deserialization without optional reactions or mentions`() {
        val release = json.decodeFromString<Release>(SAMPLE_MINIMAL_RELEASE_JSON)
        expectSelfie(prettyJson.encodeToString(release)).toMatchDisk()
    }
}
