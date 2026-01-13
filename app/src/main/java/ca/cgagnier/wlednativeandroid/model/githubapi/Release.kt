package ca.cgagnier.wlednativeandroid.model.githubapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Release(
    @field:Json(name = "url") var url: String,
    @field:Json(name = "assets_url") var assetsUrl: String,
    @field:Json(name = "upload_url") var uploadUrl: String,
    @field:Json(name = "html_url") var htmlUrl: String,
    @field:Json(name = "id") var id: Int,
    @field:Json(name = "author") var author: Author,
    @field:Json(name = "node_id") var nodeId: String,
    @field:Json(name = "tag_name") var tagName: String,
    @field:Json(name = "target_commitish") var targetCommitish: String,
    @field:Json(name = "name") var name: String,
    @field:Json(name = "draft") var draft: Boolean,
    @field:Json(name = "prerelease") var prerelease: Boolean,
    @field:Json(name = "created_at") var createdAt: String,
    @field:Json(name = "published_at") var publishedAt: String,
    @field:Json(name = "assets") var assets: List<Asset>,
    @field:Json(name = "tarball_url") var tarballUrl: String,
    @field:Json(name = "zipball_url") var zipballUrl: String,
    @field:Json(name = "body") var body: String,
    @field:Json(name = "reactions") var reactions: Reactions?,
    @field:Json(name = "mentions_count") var mentionsCount: Int?,
)
