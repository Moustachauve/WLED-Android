package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass


@JsonClass(generateAdapter = true)
data class UserMods(

    // Battery values
    @param:Json(name = "Battery level") val batteryLevel: List<Any>? = null,
    @param:Json(name = "Battery voltage") val batteryVoltage: List<Any>? = null,
)