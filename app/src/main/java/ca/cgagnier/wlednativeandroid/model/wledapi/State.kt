package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class State(

    @Json(name = "on") val isOn: Boolean? = null,
    @Json(name = "bri") val brightness: Int? = null,
    @Json(name = "transition") val transition: Int? = null,
    @Json(name = "ps") val selectedPresetId: Int? = null,
    @Json(name = "pl") val selectedPlaylistId: Int? = null,
    @Json(name = "nl") val nightlight: Nightlight? = null,
    @Json(name = "lor") val liveDataOverride: Int? = null,
    @Json(name = "mainseg") val mainSegment: Int? = null,
    @Json(name = "seg") val segment: List<Segment>? = null
)