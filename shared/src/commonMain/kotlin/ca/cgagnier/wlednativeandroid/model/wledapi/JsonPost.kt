package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JsonPost(
    @SerialName("on") val isOn: Boolean? = null,
    @SerialName("bri") val brightness: Int? = null,

    // "v" will make the post request return the current state of the device
    // So we can also update the UI while setting values
    @SerialName("v") val verbose: Boolean = true,
)
