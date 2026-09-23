package ca.cgagnier.wlednativeandroid.model.githubapi

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private val SAMPLE_RELEASE_JSON = """
    {
        "url": "https://api.github.com/repos/Aircoookie/WLED/releases/12345",
        "assets_url": "https://api.github.com/repos/Aircoookie/WLED/releases/12345/assets",
        "upload_url": "https://uploads.github.com/repos/Aircoookie/WLED/releases/12345/assets{?name,label}",
        "html_url": "https://github.com/Aircoookie/WLED/releases/tag/v0.14.0",
        "id": 12345,
        "node_id": "MDc6UmVsZWFzZTEyMzQ1",
        "tag_name": "v0.14.0",
        "target_commitish": "main",
        "name": "WLED 0.14.0 H скидки",
        "draft": false,
        "prerelease": false,
        "created_at": "2023-10-13T12:00:00Z",
        "published_at": "2023-10-13T13:00:00Z",
        "tarball_url": "https://api.github.com/repos/Aircoookie/WLED/tarball/v0.14.0",
        "zipball_url": "https://api.github.com/repos/Aircoookie/WLED/zipball/v0.14.0",
        "body": "## Release Notes\n* Awesome new feature",
        "author": {
            "login": "Aircoookie",
            "id": 100,
            "node_id": "MDQ6VXNlcjEwMA==",
            "avatar_url": "https://avatars.githubusercontent.com/u/100?v=4",
            "gravatar_id": "",
            "url": "https://api.github.com/users/Aircoookie",
            "html_url": "https://github.com/Aircoookie",
            "followers_url": "https://api.github.com/users/Aircoookie/followers",
            "following_url": "https://api.github.com/users/Aircoookie/following{/other_user}",
            "gists_url": "https://api.github.com/users/Aircoookie/gists{/gist_id}",
            "starred_url": "https://api.github.com/users/Aircoookie/starred{/owner}{/repo}",
            "subscriptions_url": "https://api.github.com/users/Aircoookie/subscriptions",
            "organizations_url": "https://api.github.com/users/Aircoookie/orgs",
            "repos_url": "https://api.github.com/users/Aircoookie/repos",
            "events_url": "https://api.github.com/users/Aircoookie/events{/privacy}",
            "received_events_url": "https://api.github.com/users/Aircoookie/received_events",
            "type": "User",
            "site_admin": false
        },
        "assets": [
            {
                "url": "https://api.github.com/repos/Aircoookie/WLED/releases/assets/999",
                "id": 999,
                "node_id": "MDEyOlJlbGVhc2VBc3NldDk5OQ==",
                "name": "WLED_0.14.0_ESP32.bin",
                "label": null,
                "content_type": "application/octet-stream",
                "state": "uploaded",
                "size": 1546200,
                "download_count": 5000,
                "created_at": "2023-10-13T12:30:00Z",
                "updated_at": "2023-10-13T12:35:00Z",
                "browser_download_url": "https://github.com/Aircoookie/WLED/releases/download/v0.14.0/WLED_0.14.0_ESP32.bin",
                "uploader": {
                    "login": "Aircoookie",
                    "id": 100,
                    "node_id": "MDQ6VXNlcjEwMA==",
                    "avatar_url": "https://avatars.githubusercontent.com/u/100?v=4",
                    "gravatar_id": "",
                    "url": "https://api.github.com/users/Aircoookie",
                    "html_url": "https://github.com/Aircoookie",
                    "followers_url": "https://api.github.com/users/Aircoookie/followers",
                    "following_url": "https://api.github.com/users/Aircoookie/following{/other_user}",
                    "gists_url": "https://api.github.com/users/Aircoookie/gists{/gist_id}",
                    "starred_url": "https://api.github.com/users/Aircoookie/starred{/owner}{/repo}",
                    "subscriptions_url": "https://api.github.com/users/Aircoookie/subscriptions",
                    "organizations_url": "https://api.github.com/users/Aircoookie/orgs",
                    "repos_url": "https://api.github.com/users/Aircoookie/repos",
                    "events_url": "https://api.github.com/users/Aircoookie/events{/privacy}",
                    "received_events_url": "https://api.github.com/users/Aircoookie/received_events",
                    "type": "User",
                    "site_admin": false
                }
            }
        ],
        "reactions": {
            "url": "https://api.github.com/repos/Aircoookie/WLED/releases/12345/reactions",
            "total_count": 42,
            "+1": 35,
            "-1": 0,
            "laugh": 1,
            "hooray": 2,
            "confused": 0,
            "heart": 3,
            "rocket": 1,
            "eyes": 0
        },
        "mentions_count": 5
    }
""".trimIndent()

private val SAMPLE_MINIMAL_RELEASE_JSON = """
    {
        "url": "https://api.github.com/repos/Aircoookie/WLED/releases/1",
        "assets_url": "https://api.github.com/repos/Aircoookie/WLED/releases/1/assets",
        "upload_url": "https://uploads.github.com/repos/Aircoookie/WLED/releases/1/assets",
        "html_url": "https://github.com/Aircoookie/WLED/releases/tag/v0.14.0-b1",
        "id": 1,
        "node_id": "MDc6UmVsZWFzZTE=",
        "tag_name": "v0.14.0-b1",
        "target_commitish": "dev",
        "name": "WLED Beta",
        "draft": false,
        "prerelease": true,
        "created_at": "2023-09-01T12:00:00Z",
        "published_at": "2023-09-01T13:00:00Z",
        "tarball_url": "",
        "zipball_url": "",
        "body": "Beta test",
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

    @Test
    fun `test Release deserialization with reactions and assets`() {
        val release = json.decodeFromString<Release>(SAMPLE_RELEASE_JSON)

        assertEquals("v0.14.0", release.tagName)
        assertEquals(12345, release.id)
        assertEquals("Aircoookie", release.author.login)
        assertEquals(1, release.assets.size)

        val asset = release.assets.first()
        assertEquals("WLED_0.14.0_ESP32.bin", asset.name)
        assertEquals(1546200L, asset.size)
        assertNull(asset.label)
        assertEquals("Aircoookie", asset.uploader.login)

        val reactions = release.reactions
        assertNotNull(reactions)
        assertEquals(42, reactions?.totalCount)
        assertEquals(35, reactions?.positive)
        assertEquals(0, reactions?.negative)
        assertEquals(3, reactions?.heart)
        assertEquals(5, release.mentionsCount)
    }

    @Test
    fun `test Release deserialization without optional reactions or mentions`() {
        val release = json.decodeFromString<Release>(SAMPLE_MINIMAL_RELEASE_JSON)
        assertEquals("v0.14.0-b1", release.tagName)
        assertNull(release.reactions)
        assertNull(release.mentionsCount)
    }
}
