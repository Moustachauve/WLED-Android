package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

@Serializable
data class UserMods(
    // Battery values
    @SerialName("Battery level") val batteryLevel: List<JsonElement>? = null,
    @SerialName("Battery voltage") val batteryVoltage: List<JsonElement>? = null,
) {
    val batteryPercentage: Double?
        get() = (batteryLevel?.firstOrNull() as? JsonPrimitive)?.doubleOrNull

    val batteryVoltageValue: Double?
        get() = (batteryVoltage?.firstOrNull() as? JsonPrimitive)?.doubleOrNull
}
