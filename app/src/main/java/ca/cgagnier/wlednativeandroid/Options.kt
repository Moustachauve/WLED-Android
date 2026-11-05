package ca.cgagnier.wlednativeandroid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Options(
    @SerialName("version")
    val version: Int,
    @SerialName("lastSelectedAddress")
    val lastSelectedAddress: String,
)
