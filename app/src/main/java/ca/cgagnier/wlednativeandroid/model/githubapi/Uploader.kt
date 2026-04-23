package ca.cgagnier.wlednativeandroid.model.githubapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Uploader(
    @field:Json(name = "login") var login: String,
    @field:Json(name = "id") var id: Int,
    @field:Json(name = "node_id") var nodeId: String,
    @field:Json(name = "avatar_url") var avatarUrl: String,
    @field:Json(name = "gravatar_id") var gravatarId: String,
    @field:Json(name = "url") var url: String,
    @field:Json(name = "html_url") var htmlUrl: String,
    @field:Json(name = "followers_url") var followersUrl: String,
    @field:Json(name = "following_url") var followingUrl: String,
    @field:Json(name = "gists_url") var gistsUrl: String,
    @field:Json(name = "starred_url") var starredUrl: String,
    @field:Json(name = "subscriptions_url") var subscriptionsUrl: String,
    @field:Json(name = "organizations_url") var organizationsUrl: String,
    @field:Json(name = "repos_url") var reposUrl: String,
    @field:Json(name = "events_url") var eventsUrl: String,
    @field:Json(name = "received_events_url") var receivedEventsUrl: String,
    @field:Json(name = "type") var type: String,
    @field:Json(name = "site_admin") var siteAdmin: Boolean,
)
