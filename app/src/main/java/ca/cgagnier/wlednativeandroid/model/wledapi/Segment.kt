package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Segment (

	@param:Json(name = "id") val id : Int? = null,
	@param:Json(name = "start") val start : Int? = null,
	@param:Json(name = "stop") val stop : Int? = null,
	@param:Json(name = "len") val length : Int? = null,
	@param:Json(name = "grp") val grouping : Int? = null,
	@param:Json(name = "spc") val spacing : Int? = null,
	@param:Json(name = "on") val isOn : Boolean? = null,
	@param:Json(name = "bri") val brightness : Int? = null,
	@param:Json(name = "col") val colors : List<List<Int>>? = null,
	@param:Json(name = "fx") val effect : Int? = null,
	@param:Json(name = "sx") val effectSpeed : Int? = null,
	@param:Json(name = "ix") val effectIntensity : Int? = null,
	@param:Json(name = "pal") val palette : Int? = null,
	@param:Json(name = "sel") val isSelected : Boolean? = null,
	@param:Json(name = "rev") val isReversed : Boolean? = null,
	@param:Json(name = "mi") val isMirrored : Boolean? = null
)