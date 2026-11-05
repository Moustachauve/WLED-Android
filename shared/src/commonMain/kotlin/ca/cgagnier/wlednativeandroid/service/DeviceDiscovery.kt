package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DeviceDiscovery {
    val discoveredDevices: Flow<DiscoveredDevice>
    val isDiscovering: StateFlow<Boolean>

    fun start()
    fun stop()
}
