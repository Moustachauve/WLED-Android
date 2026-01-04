package ca.cgagnier.wlednativeandroid.service.websocket

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.update.DeviceUpdateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class DeviceWithState(
    initialDevice: Device,
    deviceUpdateManager: DeviceUpdateManager? = null
) {
    var device: Device by mutableStateOf(initialDevice)
    val stateInfo: MutableState<DeviceStateInfo?> = mutableStateOf(null)
    val websocketStatus: MutableState<WebsocketStatus> =
        mutableStateOf(WebsocketStatus.DISCONNECTED)

    val updateVersionTagFlow: Flow<String?> =
        deviceUpdateManager?.getUpdateFlow(this) ?: flowOf(null)

    val isOnline: Boolean
        @JvmName("isOnline")
        get() = websocketStatus.value == WebsocketStatus.CONNECTED

    val isAPMode: Boolean
        @JvmName("isAPMode")
        get() = device.macAddress == AP_MODE_MAC_ADDRESS
}

enum class WebsocketStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED
}

/**
 * Get a DeviceWithState that can be used to represent a temporary WLED device in AP mode.
 */
fun getApModeDeviceWithState(): DeviceWithState {
    val device = DeviceWithState(
        Device(
            macAddress = AP_MODE_MAC_ADDRESS,
            address = "4.3.2.1",
        )
    )
    device.websocketStatus.value = WebsocketStatus.CONNECTED

    return device
}