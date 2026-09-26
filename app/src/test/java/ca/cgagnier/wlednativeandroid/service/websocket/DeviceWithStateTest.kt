package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceWithStateTest {

    private val sampleDevice = Device(
        macAddress = "AA:BB:CC:DD:EE:01",
        address = "192.168.1.101",
        customName = "Test Light",
        originalName = "WLED Light",
        branch = Branch.STABLE,
    )

    @Test
    fun `default values match expected disconnected state`() {
        val deviceWithState = DeviceWithState(device = sampleDevice)

        assertEquals(sampleDevice, deviceWithState.device)
        assertNull(deviceWithState.stateInfo)
        assertEquals(WebsocketStatus.DISCONNECTED, deviceWithState.websocketStatus)
        assertNull(deviceWithState.updateVersionTag)
        assertFalse(deviceWithState.isOnline)
        assertFalse(deviceWithState.isAPMode)
    }

    @Test
    fun `isOnline returns true only when status is CONNECTED`() {
        val connected = DeviceWithState(sampleDevice, websocketStatus = WebsocketStatus.CONNECTED)
        val connecting = DeviceWithState(sampleDevice, websocketStatus = WebsocketStatus.CONNECTING)
        val disconnected = DeviceWithState(sampleDevice, websocketStatus = WebsocketStatus.DISCONNECTED)

        assertTrue(connected.isOnline)
        assertFalse(connecting.isOnline)
        assertFalse(disconnected.isOnline)
    }

    @Test
    fun `isAPMode returns true when mac matches AP_MODE_MAC_ADDRESS`() {
        val apDevice = Device(macAddress = AP_MODE_MAC_ADDRESS, address = "4.3.2.1")
        val apWithState = DeviceWithState(apDevice)
        val normalWithState = DeviceWithState(sampleDevice)

        assertTrue(apWithState.isAPMode)
        assertFalse(normalWithState.isAPMode)
    }

    @Test
    fun `getApModeDeviceWithState returns connected AP mode device`() {
        val apDevice = getApModeDeviceWithState()

        assertEquals(AP_MODE_MAC_ADDRESS, apDevice.device.macAddress)
        assertEquals("4.3.2.1", apDevice.device.address)
        assertEquals(WebsocketStatus.CONNECTED, apDevice.websocketStatus)
        assertTrue(apDevice.isOnline)
        assertTrue(apDevice.isAPMode)
    }
}
