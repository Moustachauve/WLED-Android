package ca.cgagnier.wlednativeandroid.model.githubapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Asset(
    @SerialName("url") var url: String,
    @SerialName("id") var id: Int,
    @SerialName("node_id") var nodeId: String,
    @SerialName("name") var name: String,
    @SerialName("label") var label: String?,
    @SerialName("uploader") var uploader: Uploader,
    @SerialName("content_type") var contentType: String,
    @SerialName("state") var state: String,
    @SerialName("size") var size: Long,
    @SerialName("download_count") var downloadCount: Int,
    @SerialName("created_at") var createdAt: String,
    @SerialName("updated_at") var updatedAt: String,
    @SerialName("browser_download_url") var browserDownloadUrl: String,
)
