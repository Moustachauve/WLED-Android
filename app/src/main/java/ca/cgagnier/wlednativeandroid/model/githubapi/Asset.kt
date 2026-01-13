package ca.cgagnier.wlednativeandroid.model.githubapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Asset(
    @field:Json(name = "url") var url: String,
    @field:Json(name = "id") var id: Int,
    @field:Json(name = "node_id") var nodeId: String,
    @field:Json(name = "name") var name: String,
    @field:Json(name = "label") var label: String?,
    @field:Json(name = "uploader") var uploader: Uploader,
    @field:Json(name = "content_type") var contentType: String,
    @field:Json(name = "state") var state: String,
    @field:Json(name = "size") var size: Long,
    @field:Json(name = "download_count") var downloadCount: Int,
    @field:Json(name = "created_at") var createdAt: String,
    @field:Json(name = "updated_at") var updatedAt: String,
    @field:Json(name = "browser_download_url") var browserDownloadUrl: String,
)
