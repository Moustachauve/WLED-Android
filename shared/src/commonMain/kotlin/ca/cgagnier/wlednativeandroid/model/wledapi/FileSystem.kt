package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FileSystem(
    @SerialName("u") val spaceUsed: Int? = null,
    @SerialName("t") val spaceTotal: Int? = null,
    @SerialName("pmt") val presetLastModification: Int? = null,
)
