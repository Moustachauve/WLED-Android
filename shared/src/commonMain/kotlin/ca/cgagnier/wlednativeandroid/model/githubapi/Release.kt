package ca.cgagnier.wlednativeandroid.model.githubapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Release(
    @SerialName("url") var url: String,
    @SerialName("assets_url") var assetsUrl: String,
    @SerialName("upload_url") var uploadUrl: String,
    @SerialName("html_url") var htmlUrl: String,
    @SerialName("id") var id: Int,
    @SerialName("author") var author: Author,
    @SerialName("node_id") var nodeId: String,
    @SerialName("tag_name") var tagName: String,
    @SerialName("target_commitish") var targetCommitish: String,
    @SerialName("name") var name: String,
    @SerialName("draft") var draft: Boolean,
    @SerialName("prerelease") var prerelease: Boolean,
    @SerialName("created_at") var createdAt: String,
    @SerialName("published_at") var publishedAt: String,
    @SerialName("assets") var assets: List<Asset>,
    @SerialName("tarball_url") var tarballUrl: String,
    @SerialName("zipball_url") var zipballUrl: String,
    @SerialName("body") var body: String,
    @SerialName("reactions") var reactions: Reactions? = null,
    @SerialName("mentions_count") var mentionsCount: Int? = null,
)
