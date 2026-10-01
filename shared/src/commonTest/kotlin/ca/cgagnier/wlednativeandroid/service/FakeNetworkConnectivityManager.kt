package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A test double for [NetworkConnectivityManager] that enables multiplatform tests to control
 * and simulate network state transitions in memory.
 */
class FakeNetworkConnectivityManager(initialStatus: NetworkStatus) : NetworkConnectivityManager {

    constructor(
        isConnected: Boolean = false,
        isWLEDCaptivePortal: Boolean = false,
    ) : this(NetworkStatus(isConnected = isConnected, isWLEDCaptivePortal = isWLEDCaptivePortal))
    private val _networkStatus = MutableStateFlow(initialStatus)
    override val networkStatus: StateFlow<NetworkStatus> = _networkStatus.asStateFlow()

    private val _isConnected = MutableStateFlow(initialStatus.isConnected)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _isWLEDCaptivePortal = MutableStateFlow(initialStatus.isWLEDCaptivePortal)
    override val isWLEDCaptivePortal: StateFlow<Boolean> = _isWLEDCaptivePortal.asStateFlow()

    /**
     * Updates the full [NetworkStatus] atomically.
     */
    fun setStatus(status: NetworkStatus) {
        _networkStatus.value = status
        _isConnected.value = status.isConnected
        _isWLEDCaptivePortal.value = status.isWLEDCaptivePortal
    }

    /**
     * Updates the [isConnected] state.
     */
    fun setConnected(connected: Boolean) {
        setStatus(_networkStatus.value.copy(isConnected = connected))
    }

    /**
     * Updates the [isWLEDCaptivePortal] state.
     */
    fun setWLEDCaptivePortal(isCaptivePortal: Boolean) {
        setStatus(_networkStatus.value.copy(isWLEDCaptivePortal = isCaptivePortal))
    }
}
