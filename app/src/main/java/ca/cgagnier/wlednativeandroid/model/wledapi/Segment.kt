package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Segment(
    @SerialName("id") val id: Int? = null,
    @SerialName("start") val start: Int? = null,
    @SerialName("stop") val stop: Int? = null,
    @SerialName("len") val length: Int? = null,
    @SerialName("grp") val grouping: Int? = null,
    @SerialName("spc") val spacing: Int? = null,
    @SerialName("on") val isOn: Boolean? = null,
    @SerialName("bri") val brightness: Int? = null,
    @SerialName("col") val colors: List<List<Int>>? = null,
    @SerialName("fx") val effect: Int? = null,
    @SerialName("sx") val effectSpeed: Int? = null,
    @SerialName("ix") val effectIntensity: Int? = null,
    @SerialName("pal") val palette: Int? = null,
    @SerialName("sel") val isSelected: Boolean? = null,
    @SerialName("rev") val isReversed: Boolean? = null,
    @SerialName("mi") val isMirrored: Boolean? = null,
)
