package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-agnostic contract for monitoring network connectivity and WLED captive portal status.
 */
interface NetworkConnectivityManager {
    /**
     * Hot flow emitting atomic updates of the device's network connectivity status.
     */
    val networkStatus: StateFlow<NetworkStatus>

    /**
     * Convenience hot flow emitting true when the device has an active network connection.
     */
    val isConnected: StateFlow<Boolean>

    /**
     * Convenience hot flow emitting true when the active network is a WLED Access Point captive portal.
     */
    val isWLEDCaptivePortal: StateFlow<Boolean>
}
