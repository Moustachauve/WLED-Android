package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The options bitmask at 0x01 being 0 means OTA is disabled on the device.
 */
private const val OTA_ENABLED_FLAG = 0x01

@Serializable
data class Info(
    @SerialName("leds") val leds: Leds,
    @SerialName("wifi") val wifi: Wifi,
    @SerialName("ver") val version: String? = null,
    @SerialName("vid") val buildId: Int? = null,
    // Added in 0.15
    @SerialName("cn") val codeName: String? = null,
    // Added in 0.15
    @SerialName("release") val release: String? = null,
    // Added in 0.15.2
    @SerialName("repo") val repository: String? = null,
    @SerialName("name") val name: String,
    @SerialName("str") val syncToggleReceive: Boolean? = null,
    @SerialName("udpport") val udpPort: Int? = null,
    // Added in 0.15
    @SerialName("simplifiedui") val simplifiedUI: Boolean? = null,
    @SerialName("live") val isUpdatedLive: Boolean? = null,
    @SerialName("liveseg") val liveSegment: Int? = null,
    @SerialName("lm") val realtimeMode: String? = null,
    @SerialName("lip") val realtimeIp: String? = null,
    @SerialName("ws") val websocketClientCount: Int? = null,
    @SerialName("fxcount") val effectCount: Int? = null,
    @SerialName("palcount") val paletteCount: Int? = null,
    @SerialName("cpalcount") val customPaletteCount: Int? = null,
    // Missing: maps
    @SerialName("fs") val fileSystem: FileSystem? = null,
    @SerialName("ndc") val nodeListCount: Int? = null,
    @SerialName("arch") val platformName: String? = null,
    @SerialName("core") val arduinoCoreVersion: String? = null,
    // Added in 0.15
    @SerialName("clock") val clockFrequency: Int? = null,
    // Added in 0.15
    @SerialName("flash") val flashChipSize: Int? = null,
    @Deprecated(
        "lwip is deprecated and is supposed to be removed in 0.14.0",
    ) @SerialName("lwip") val lwip: Int? =
        null,
    @SerialName("freeheap") val freeHeap: Int? = null,
    @SerialName("uptime") val uptime: Int? = null,
    @SerialName("time") val time: String? = null,
    // Contains some extra options status in the form of a bitset
    @SerialName("opt") val options: Int? = null,
    @SerialName("brand") val brand: String? = null,
    @SerialName("product") val product: String? = null,
    @SerialName("mac") val macAddress: String? = null,
    @SerialName("ip") val ipAddress: String? = null,
    @SerialName("u") val userMods: UserMods? = null,
)

/**
 * Determine whether OTA updates are enabled on the device.
 */
val Info.isOtaEnabled: Boolean
    get() = options?.and(OTA_ENABLED_FLAG) != 0
