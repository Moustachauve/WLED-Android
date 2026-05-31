package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Preset(
    @Json(name = "n") val name: String = "",
    @Json(name = "on") val on: Boolean? = null,
    @Json(name = "bri") val brightness: Int? = null,
    @Json(name = "mainseg") val mainSegment: Int? = null
)
