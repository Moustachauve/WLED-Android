package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Immutable representation of a WLED device combined with its runtime state.
 *
 * All state properties are immutable, making this data class safe for concurrent
 * observation and optimal for Jetpack Compose recomposition diffing.
 *
 * @property device The underlying persistent device entity.
 * @property stateInfo The real-time device state and device information, or null if not yet received.
 * @property websocketStatus The current connection status of the device's WebSocket.
 * @property updateVersionTag The newer release tag available for this device, or null if up to date.
 */
data class DeviceWithState(
    val device: Device,
    val stateInfo: DeviceStateInfo? = null,
    val websocketStatus: WebsocketStatus = WebsocketStatus.DISCONNECTED,
    val updateVersionTag: String? = null,
) {
    val isOnline: Boolean
        get() = websocketStatus == WebsocketStatus.CONNECTED

    val isAPMode: Boolean
        get() = device.macAddress == AP_MODE_MAC_ADDRESS

    /**
     * Backward-compatibility flow bridge providing the update tag.
     * Downstream UI consumers can collect this or read [updateVersionTag] directly.
     */
    val updateVersionTagFlow: Flow<String?>
        get() = flowOf(updateVersionTag)
}

/**
 * Connection status for a WLED device's WebSocket client.
 */
enum class WebsocketStatus {
    CONNECTED,
    CONNECTING,
    DISCONNECTED,
}

/**
 * Get a DeviceWithState that can be used to represent a temporary WLED device in AP mode.
 */
fun getApModeDeviceWithState(): DeviceWithState = DeviceWithState(
    device = Device(
        macAddress = AP_MODE_MAC_ADDRESS,
        address = DEFAULT_WLED_AP_IP,
    ),
    websocketStatus = WebsocketStatus.CONNECTED,
)
