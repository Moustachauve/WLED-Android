package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Nightlight(
    @SerialName("on") val isOn: Boolean? = null,
    @SerialName("dur") val duration: Int? = null,
    @SerialName("fade") val fade: Boolean? = null,
    @SerialName("mode") val mode: Int? = null,
    @SerialName("tbri") val targetBrightness: Int? = null,
    @SerialName("rem") val remainingTime: Int? = null,
)
