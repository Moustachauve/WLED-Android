package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Wifi(
    @SerialName("bssid") val bssid: String? = null,
    @SerialName("rssi") val rssi: Int? = null,
    @SerialName("signal") val signal: Int? = null,
    @SerialName("channel") val channel: Int? = null,
    @SerialName("ap") val isApMode: Boolean? = null,
)
