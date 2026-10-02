package ca.cgagnier.wlednativeandroid.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeDeviceDiscovery : DeviceDiscovery {
    private val _discoveredDevices = MutableSharedFlow<DiscoveredDevice>(replay = 1, extraBufferCapacity = 64)
    override val discoveredDevices: SharedFlow<DiscoveredDevice> = _discoveredDevices.asSharedFlow()

    private val _isDiscovering = MutableStateFlow(false)
    override val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    override fun start() {
        _isDiscovering.value = true
    }

    override fun stop() {
        _isDiscovering.value = false
    }

    suspend fun emitDevice(device: DiscoveredDevice) {
        _discoveredDevices.emit(device)
    }

    fun emitDeviceSync(device: DiscoveredDevice) {
        _discoveredDevices.tryEmit(device)
    }
}
