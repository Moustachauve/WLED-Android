package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Wifi (

	@param:Json(name = "bssid") val bssid : String? = null,
	@param:Json(name = "rssi") val rssi : Int? = null,
	@param:Json(name = "signal") val signal : Int? = null,
	@param:Json(name = "channel") val channel : Int? = null,
	@param:Json(name = "ap") val isApMode : Boolean? = null,
)