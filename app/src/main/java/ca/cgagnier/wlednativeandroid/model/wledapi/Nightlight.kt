package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Nightlight (

	@param:Json(name = "on") val isOn : Boolean? = null,
	@param:Json(name = "dur") val duration : Int? = null,
	@param:Json(name = "fade") val fade : Boolean? = null,
	@param:Json(name = "mode") val mode : Int? = null,
	@param:Json(name = "tbri") val targetBrightness : Int? = null,
	@param:Json(name = "rem") val remainingTime : Int? = null
)