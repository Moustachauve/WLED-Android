package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Leds (

	@param:Json(name = "count") val count : Int? = null,
	@param:Json(name = "pwr") val estimatedPowerUsed : Int? = null,
	@param:Json(name = "fps") val fps : Int? = null,
	@param:Json(name = "maxpwr") val maxPower : Int? = null,
	@param:Json(name = "maxseg") val maxSegment : Int? = null
)