package ca.cgagnier.wlednativeandroid.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.UserMods
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import java.util.concurrent.TimeUnit

class DevicePreviewParameterProvider : PreviewParameterProvider<DeviceWithState> {
    private val fakeCurrentTime = System.currentTimeMillis()
    override val values = sequenceOf(
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
                originalName = "original name",
                customName = "custom name",
                lastSeen = fakeCurrentTime
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.CONNECTED
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS, address = "4.3.2.1", lastSeen = fakeCurrentTime
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.CONNECTING
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
                originalName = "original name",
                lastSeen = fakeCurrentTime - TimeUnit.MINUTES.toMillis(45)
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.DISCONNECTED
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "very-long-address-that-takes-more-than-a-full-width-so-should-be-truncated",
                originalName = "Very long name that should also be truncated if everything is working",
                lastSeen = fakeCurrentTime
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.DISCONNECTED
        },
        DeviceWithState(
            Device(
                macAddress = AP_MODE_MAC_ADDRESS,
                address = "4.3.2.1",
                originalName = "device with battery",
                lastSeen = fakeCurrentTime
            )
        ).apply {
            websocketStatus.value = WebsocketStatus.CONNECTED
            stateInfo.value = DeviceStateInfo(
                State(isOn = true, brightness = 128, transition = 7), Info(
                    version = "0.14.0",
                    leds = Leds(count = 60),
                    name = "WLED",
                    wifi = Wifi(bssid = "ff:ee:dd:cc:bb:aa", rssi = -65, signal = 70, channel = 6),
                    userMods = UserMods(
                        batteryLevel = listOf(75.0)
                    )
                )
            )
        },
    )
}

fun getPreviewDevice(): DeviceWithState {
    return DeviceWithState(
        Device(
            macAddress = "00:11:22:33:44:55",
            address = "192.168.1.123",
            customName = "Preview Device"
        )
    )
}
