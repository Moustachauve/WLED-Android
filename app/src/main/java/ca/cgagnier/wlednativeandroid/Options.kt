package ca.cgagnier.wlednativeandroid

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Options(
    @param:Json(name = "version")
    val version: Int,
    @param:Json(name = "lastSelectedAddress")
    val lastSelectedAddress: String
)