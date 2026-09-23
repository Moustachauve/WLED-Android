package ca.cgagnier.wlednativeandroid.model.githubapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Reactions(
    @SerialName("url") var url: String,
    @SerialName("total_count") var totalCount: Int,
    @SerialName("+1") var positive: Int,
    @SerialName("-1") var negative: Int,
    @SerialName("laugh") var laugh: Int,
    @SerialName("hooray") var hooray: Int,
    @SerialName("confused") var confused: Int,
    @SerialName("heart") var heart: Int,
    @SerialName("rocket") var rocket: Int,
    @SerialName("eyes") var eyes: Int,
)
