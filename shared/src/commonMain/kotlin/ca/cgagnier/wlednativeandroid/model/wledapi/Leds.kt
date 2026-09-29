package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Leds(
    @SerialName("count") val count: Int? = null,
    @SerialName("pwr") val estimatedPowerUsed: Int? = null,
    @SerialName("fps") val fps: Int? = null,
    @SerialName("maxpwr") val maxPower: Int? = null,
    @SerialName("maxseg") val maxSegment: Int? = null,
)
