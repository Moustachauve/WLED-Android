package ca.cgagnier.wlednativeandroid.model.githubapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Reactions(
    @field:Json(name = "url") var url: String,
    @field:Json(name = "total_count") var totalCount: Int,
    @field:Json(name = "+1") var positive: Int,
    @field:Json(name = "-1") var negative: Int,
    @field:Json(name = "laugh") var laugh: Int,
    @field:Json(name = "hooray") var hooray: Int,
    @field:Json(name = "confused") var confused: Int,
    @field:Json(name = "heart") var heart: Int,
    @field:Json(name = "rocket") var rocket: Int,
    @field:Json(name = "eyes") var eyes: Int,
)
