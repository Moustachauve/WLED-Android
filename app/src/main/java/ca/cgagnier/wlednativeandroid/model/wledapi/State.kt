package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class State(

    @param:Json(name = "on") val isOn: Boolean? = null,
    @param:Json(name = "bri") val brightness: Int? = null,
    @param:Json(name = "transition") val transition: Int? = null,
    @param:Json(name = "ps") val selectedPresetId: Int? = null,
    @param:Json(name = "pl") val selectedPlaylistId: Int? = null,
    @param:Json(name = "nl") val nightlight: Nightlight? = null,
    @param:Json(name = "lor") val liveDataOverride: Int? = null,
    @param:Json(name = "mainseg") val mainSegment: Int? = null,
    @param:Json(name = "seg") val segment: List<Segment>? = null
)