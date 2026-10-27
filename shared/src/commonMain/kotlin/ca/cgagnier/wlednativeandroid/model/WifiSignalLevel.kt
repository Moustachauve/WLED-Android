package ca.cgagnier.wlednativeandroid.model

import kotlinx.serialization.Serializable

/**
 * Connectivity and WiFi signal strength level for a WLED device, derived from its online state
 * and RSSI in dBm.
 *
 * Consumed by both Android and iOS device lists and Home Screen widgets so each platform can map
 * discrete levels to platform-native icons without duplicating RSSI threshold logic.
 */
@Serializable
enum class WifiSignalLevel {
    OFFLINE,
    NONE,
    WEAK,
    FAIR,
    GOOD,
    ;

    companion object {
        private const val GOOD_RSSI_THRESHOLD = -70
        private const val FAIR_RSSI_THRESHOLD = -80
        private const val WEAK_RSSI_THRESHOLD = -100

        /**
         * Resolves the [WifiSignalLevel] for a device given its online status and RSSI in dBm.
         *
         * @param rssi The WiFi RSSI in dBm (negative values), or null/0 if unknown.
         * @param isOnline Whether the device is currently reachable.
         * @param defaultWhenNull The level to return when the device is online but [rssi] is null
         *   or 0 (e.g. [GOOD] for HTTP-only widget updates where RSSI is unavailable, or
         *   [NONE]/[OFFLINE] in the device list before `info.wifi.rssi` arrives).
         */
        fun fromRssi(rssi: Int?, isOnline: Boolean, defaultWhenNull: WifiSignalLevel = GOOD): WifiSignalLevel = when {
            !isOnline -> OFFLINE
            rssi == null || rssi == 0 -> defaultWhenNull
            rssi >= GOOD_RSSI_THRESHOLD -> GOOD
            rssi >= FAIR_RSSI_THRESHOLD -> FAIR
            rssi >= WEAK_RSSI_THRESHOLD -> WEAK
            else -> NONE
        }
    }
}
