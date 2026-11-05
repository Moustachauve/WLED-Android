package ca.cgagnier.wlednativeandroid.model.githubapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Uploader(
    @SerialName("login") var login: String,
    @SerialName("id") var id: Int,
    @SerialName("node_id") var nodeId: String,
    @SerialName("avatar_url") var avatarUrl: String,
    @SerialName("gravatar_id") var gravatarId: String,
    @SerialName("url") var url: String,
    @SerialName("html_url") var htmlUrl: String,
    @SerialName("followers_url") var followersUrl: String,
    @SerialName("following_url") var followingUrl: String,
    @SerialName("gists_url") var gistsUrl: String,
    @SerialName("starred_url") var starredUrl: String,
    @SerialName("subscriptions_url") var subscriptionsUrl: String,
    @SerialName("organizations_url") var organizationsUrl: String,
    @SerialName("repos_url") var reposUrl: String,
    @SerialName("events_url") var eventsUrl: String,
    @SerialName("received_events_url") var receivedEventsUrl: String,
    @SerialName("type") var type: String,
    @SerialName("site_admin") var siteAdmin: Boolean,
)
