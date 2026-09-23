package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class State(
    @SerialName("on") val isOn: Boolean? = null,
    @SerialName("bri") val brightness: Int? = null,
    @SerialName("transition") val transition: Int? = null,
    @SerialName("ps") val selectedPresetId: Int? = null,
    @SerialName("pl") val selectedPlaylistId: Int? = null,
    @SerialName("nl") val nightlight: Nightlight? = null,
    @SerialName("lor") val liveDataOverride: Int? = null,
    @SerialName("mainseg") val mainSegment: Int? = null,
    @SerialName("seg") val segment: List<Segment>? = null,
)
