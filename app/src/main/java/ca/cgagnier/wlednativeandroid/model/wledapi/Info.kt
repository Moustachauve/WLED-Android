package ca.cgagnier.wlednativeandroid.model.wledapi

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The options bitmask at 0x01 being 0 means OTA is disabled on the device.
 */
private const val OTA_ENABLED_FLAG = 0x01

@JsonClass(generateAdapter = true)
data class Info(

    @param:Json(name = "leds") val leds: Leds,
    @param:Json(name = "wifi") val wifi: Wifi,
    @param:Json(name = "ver") val version: String? = null,
    @param:Json(name = "vid") val buildId: Int? = null,
    // Added in 0.15
    @param:Json(name = "cn") val codeName: String? = null,
    // Added in 0.15
    @param:Json(name = "release") val release: String? = null,
    @param:Json(name = "name") val name: String,
    @param:Json(name = "str") val syncToggleReceive: Boolean? = null,
    @param:Json(name = "udpport") val udpPort: Int? = null,
    // Added in 0.15
    @param:Json(name = "simplifiedui") val simplifiedUI: Boolean? = null,
    @param:Json(name = "live") val isUpdatedLive: Boolean? = null,
    @param:Json(name = "liveseg") val liveSegment: Int? = null,
    @param:Json(name = "lm") val realtimeMode: String? = null,
    @param:Json(name = "lip") val realtimeIp: String? = null,
    @param:Json(name = "ws") val websocketClientCount: Int? = null,
    @param:Json(name = "fxcount") val effectCount: Int? = null,
    @param:Json(name = "palcount") val paletteCount: Int? = null,
    @param:Json(name = "cpalcount") val customPaletteCount: Int? = null,
    // Missing: maps
    @param:Json(name = "fs") val fileSystem: FileSystem? = null,
    @param:Json(name = "ndc") val nodeListCount: Int? = null,
    @param:Json(name = "arch") val platformName: String? = null,
    @param:Json(name = "core") val arduinoCoreVersion: String? = null,
    // Added in 0.15
    @param:Json(name = "clock") val clockFrequency: Int? = null,
    // Added in 0.15
    @param:Json(name = "flash") val flashChipSize: Int? = null,
    @Deprecated("lwip is deprecated and is supposed to be removed in 0.14.0") @param:Json(name = "lwip") val lwip: Int? = null,
    @param:Json(name = "freeheap") val freeHeap: Int? = null,
    @param:Json(name = "uptime") val uptime: Int? = null,
    @param:Json(name = "time") val time: String? = null,
    // Contains some extra options status in the form of a bitset
    @param:Json(name = "opt") val options: Int? = null,
    @param:Json(name = "brand") val brand: String? = null,
    @param:Json(name = "product") val product: String? = null,
    @param:Json(name = "mac") val macAddress: String? = null,
    @param:Json(name = "ip") val ipAddress: String? = null,
    @param:Json(name = "u") val userMods: UserMods? = null
)

/**
 * Determine whether OTA updates are enabled on the device.
 */
val Info.isOtaEnabled: Boolean
    get() = options?.and(OTA_ENABLED_FLAG) != 0