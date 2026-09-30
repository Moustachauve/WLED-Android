package ca.cgagnier.wlednativeandroid.service

/**
 * Represents a platform-agnostic snapshot of the device's network connectivity status.
 *
 * @property isConnected True if the device currently has an active network connection.
 * @property isWLEDCaptivePortal True if the active network is identified as a WLED Access Point (AP) captive portal.
 */
data class NetworkStatus(val isConnected: Boolean = false, val isWLEDCaptivePortal: Boolean = false)
