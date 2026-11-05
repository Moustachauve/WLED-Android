package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import com.android.tools.screenshot.PreviewTest
import java.util.concurrent.TimeUnit

class DeviceListScreenshotTest {

    @PreviewTest
    @Preview(showBackground = true)
    @Composable
    fun DeviceListLightPreview() {
        WLEDNativeTheme(darkTheme = false, dynamicColor = false) {
            DeviceListContent(state = createTestDeviceListState())
        }
    }

    @PreviewTest
    @Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
    @Composable
    fun DeviceListDarkPreview() {
        WLEDNativeTheme(darkTheme = true, dynamicColor = false) {
            DeviceListContent(state = createTestDeviceListState())
        }
    }

    private fun createTestDeviceListState(): DeviceListState {
        val fakeCurrentTime = 1_700_000_000_000L
        return DeviceListState(
            allDevices = listOf(
                createLivingRoomDevice(fakeCurrentTime),
                createDeskLampDevice(fakeCurrentTime),
                createOfflineBedroomDevice(fakeCurrentTime),
                createHiddenClosetDevice(fakeCurrentTime),
            ),
            showOfflineDevicesLast = true,
            showHiddenDevices = false,
            isInitialLoading = false,
            currentTime = fakeCurrentTime,
        )
    }

    private fun createLivingRoomDevice(fakeCurrentTime: Long) = DeviceWithState(
        device = Device(
            macAddress = "AA:BB:CC:DD:EE:01",
            address = "192.168.1.101",
            originalName = "Living Room WLED",
            lastSeen = fakeCurrentTime,
        ),
        websocketStatus = WebsocketStatus.CONNECTED,
        stateInfo = DeviceStateInfo.createMock(
            name = "Living Room WLED",
            version = "0.15.0",
            brightness = 180,
            isOn = true,
        ),
    )

    private fun createDeskLampDevice(fakeCurrentTime: Long) = DeviceWithState(
        device = Device(
            macAddress = "AA:BB:CC:DD:EE:04",
            address = "192.168.1.104",
            originalName = "WLED-Desk",
            customName = "Desk Lamp",
            lastSeen = fakeCurrentTime,
        ),
        websocketStatus = WebsocketStatus.CONNECTED,
        updateVersionTag = "0.15.1",
        stateInfo = DeviceStateInfo.createMock(
            name = "WLED-Desk",
            version = "0.14.4",
            red = 0,
            green = 180,
            blue = 255,
            brightness = 64,
            isOn = false,
        ).withSignal(-85).withBatteryLevel(75.0),
    )

    private fun createOfflineBedroomDevice(fakeCurrentTime: Long) = DeviceWithState(
        device = Device(
            macAddress = "AA:BB:CC:DD:EE:02",
            address = "192.168.1.102",
            originalName = "Bedroom Strip",
            lastSeen = fakeCurrentTime - TimeUnit.MINUTES.toMillis(45),
        ),
        websocketStatus = WebsocketStatus.DISCONNECTED,
    )

    private fun createHiddenClosetDevice(fakeCurrentTime: Long) = DeviceWithState(
        device = Device(
            macAddress = "AA:BB:CC:DD:EE:03",
            address = "192.168.1.103",
            originalName = "Hidden Closet Light",
            isHidden = true,
            lastSeen = fakeCurrentTime,
        ),
        websocketStatus = WebsocketStatus.CONNECTED,
        stateInfo = DeviceStateInfo.createMock(
            name = "Hidden Closet Light",
            version = "0.15.0",
            brightness = 128,
            isOn = true,
        ),
    )
}
