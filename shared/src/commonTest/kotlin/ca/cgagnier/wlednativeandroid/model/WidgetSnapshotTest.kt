package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.Leds
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.Wifi
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WidgetSnapshotTest {

    @Suppress("LongParameterList")
    private fun createDeviceWithState(
        customName: String = "",
        originalName: String = "",
        isOn: Boolean = true,
        brightness: Int = 128,
        colors: List<List<Int>>? = null,
        status: WebsocketStatus = WebsocketStatus.CONNECTED,
    ): DeviceWithState {
        val segment = colors?.let { listOf(Segment(colors = it)) }
        val state = State(isOn = isOn, brightness = brightness, segment = segment)
        val info = Info(
            version = "0.14.0",
            leds = Leds(count = 30),
            wifi = Wifi(),
            name = originalName,
        )
        return DeviceWithState(
            device = Device(
                macAddress = "AABBCCDDEEFF",
                address = "192.168.1.100",
                customName = customName,
                originalName = originalName,
            ),
            stateInfo = DeviceStateInfo(state = state, info = info),
            websocketStatus = status,
        )
    }

    @Test
    fun toWidgetSnapshotProjectsNameUsingCustomName() {
        val device = createDeviceWithState(customName = "Living Room", originalName = "WLED-Device")
        val snapshot = device.toWidgetSnapshot(defaultName = "Default")
        assertEquals("Living Room", snapshot.name)
    }

    @Test
    fun toWidgetSnapshotFallsBackToOriginalNameWhenCustomNameBlank() {
        val device = createDeviceWithState(customName = "", originalName = "WLED-Device")
        val snapshot = device.toWidgetSnapshot(defaultName = "Default")
        assertEquals("WLED-Device", snapshot.name)
    }

    @Test
    fun toWidgetSnapshotFallsBackToDefaultNameWhenBothNamesBlank() {
        val device = createDeviceWithState(customName = "", originalName = "")
        val snapshot = device.toWidgetSnapshot(defaultName = "Default Light")
        assertEquals("Default Light", snapshot.name)
    }

    @Test
    fun toWidgetSnapshotExtractsPrimaryColorFromStateSegment() {
        val device = createDeviceWithState(colors = listOf(listOf(255, 0, 0)))
        val snapshot = device.toWidgetSnapshot(defaultName = "Default")
        // 0xFFFF0000 -> -65536
        assertEquals(-65536, snapshot.color)
    }

    @Test
    fun toWidgetSnapshotUsesFallbackColorWhenSegmentColorsMissing() {
        val device = createDeviceWithState(colors = null)
        val snapshot = device.toWidgetSnapshot(defaultName = "Default", fallbackColor = -1)
        assertEquals(-1, snapshot.color)
    }

    @Test
    fun getColorFromStateParsesRgbValuesInto32BitArgbInteger() {
        val state = State(segment = listOf(Segment(colors = listOf(listOf(0, 255, 0)))))
        val color = getColorFromState(state)
        // 0xFF00FF00 -> -16711936
        assertEquals(-16711936, color)
    }

    @Test
    fun toWidgetSnapshotTrimsLeadingAndTrailingWhitespaceFromNames() {
        val device = createDeviceWithState(customName = "  Living Room  ", originalName = "  WLED-Device  ")
        val snapshot = device.toWidgetSnapshot(defaultName = "Default")
        assertEquals("Living Room", snapshot.name)
    }

    @Test
    fun getColorFromStateReturnsNullWhenSegmentArrayIsShort() {
        val state = State(segment = listOf(Segment(colors = listOf(listOf(255, 255)))))
        val color = getColorFromState(state)
        assertNull(color)
    }

    @Test
    fun getColorFromStateReturnsNullWhenSegmentArrayExceedsFourElements() {
        val state = State(segment = listOf(Segment(colors = listOf(listOf(255, 255, 255, 0, 1)))))
        val color = getColorFromState(state)
        assertNull(color)
    }
}
