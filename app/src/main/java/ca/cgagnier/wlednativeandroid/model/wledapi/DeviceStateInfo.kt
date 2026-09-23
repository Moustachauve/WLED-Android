package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceStateInfo(@SerialName("state") val state: State, @SerialName("info") val info: Info)
