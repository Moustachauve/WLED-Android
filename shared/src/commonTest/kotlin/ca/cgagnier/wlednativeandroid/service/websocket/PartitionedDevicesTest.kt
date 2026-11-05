package ca.cgagnier.wlednativeandroid.service.websocket

import ca.cgagnier.wlednativeandroid.model.Device
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartitionedDevicesTest {

    @Suppress("LongParameterList")
    private fun createDevice(
        mac: String,
        address: String = "192.168.1.100",
        customName: String = "",
        originalName: String = "",
        isHidden: Boolean = false,
        lastSeen: Long = 10_000L,
        status: WebsocketStatus = WebsocketStatus.DISCONNECTED,
    ): DeviceWithState = DeviceWithState(
        device = Device(
            macAddress = mac,
            address = address,
            customName = customName,
            originalName = originalName,
            isHidden = isHidden,
            lastSeen = lastSeen,
        ),
        websocketStatus = status,
    )

    @Test
    fun visibleDevicesIncludesNonHiddenDevices() {
        val devices = listOf(createDevice(mac = "A", isHidden = false))
        val result = partitionDevices(devices, showHiddenDevices = false, currentTimeMillis = 10_000L)
        assertEquals(1, result.visible.size)
    }

    @Test
    fun visibleDevicesExcludesHiddenDevicesWhenShowHiddenDevicesIsFalse() {
        val devices = listOf(
            createDevice(mac = "A", isHidden = false),
            createDevice(mac = "B", isHidden = true),
        )
        val result = partitionDevices(devices, showHiddenDevices = false, currentTimeMillis = 10_000L)
        assertEquals(1, result.visible.size)
        assertEquals("A", result.visible[0].device.macAddress)
    }

    @Test
    fun visibleDevicesIncludesHiddenDevicesWhenShowHiddenDevicesIsTrue() {
        val devices = listOf(
            createDevice(mac = "A", isHidden = false),
            createDevice(mac = "B", isHidden = true),
        )
        val result = partitionDevices(devices, showHiddenDevices = true, currentTimeMillis = 10_000L)
        assertEquals(2, result.visible.size)
    }

    @Test
    fun devicesAreSortedCaseInsensitivelyByCustomName() {
        val devices = listOf(
            createDevice(mac = "1", customName = "zebra"),
            createDevice(mac = "2", customName = "Apple"),
            createDevice(mac = "3", customName = "banana"),
        )
        val result = partitionDevices(devices, showHiddenDevices = false, currentTimeMillis = 10_000L)
        val names = result.visible.map { it.device.customName }
        assertEquals(listOf("Apple", "banana", "zebra"), names)
    }

    @Test
    fun devicesFallBackToOriginalNameWhenCustomNameIsBlank() {
        val devices = listOf(
            createDevice(mac = "1", customName = "", originalName = "Zulu"),
            createDevice(mac = "2", customName = "Alpha", originalName = "Beta"),
        )
        val result = partitionDevices(devices, showHiddenDevices = false, currentTimeMillis = 10_000L)
        assertEquals("Alpha", result.visible[0].device.customName)
        assertEquals("Zulu", result.visible[1].device.originalName)
    }

    @Test
    fun connectedDeviceIsPlacedInOnlineList() {
        val devices = listOf(createDevice(mac = "A", status = WebsocketStatus.CONNECTED, lastSeen = 0L))
        val result = partitionDevices(devices, showHiddenDevices = false, currentTimeMillis = 100_000L)
        assertTrue(result.online.any { it.device.macAddress == "A" })
        assertFalse(result.offline.any { it.device.macAddress == "A" })
    }

    @Test
    fun disconnectedDeviceWithinGracePeriodIsPlacedInOnlineList() {
        val devices = listOf(
            createDevice(
                mac = "A",
                status = WebsocketStatus.DISCONNECTED,
                lastSeen = 50_000L,
            ),
        )
        // 70_000 - 50_000 = 20_000ms < 60_000ms grace period
        val result = partitionDevices(
            devices,
            showHiddenDevices = false,
            currentTimeMillis = 70_000L,
            offlineGracePeriodMs = 60_000L,
        )
        assertTrue(result.online.any { it.device.macAddress == "A" })
        assertTrue(result.offline.isEmpty())
    }

    @Test
    fun disconnectedDeviceBeyondGracePeriodIsPlacedInOfflineList() {
        val devices = listOf(
            createDevice(
                mac = "A",
                status = WebsocketStatus.DISCONNECTED,
                lastSeen = 10_000L,
            ),
        )
        // 80_000 - 10_000 = 70_000ms >= 60_000ms grace period
        val result = partitionDevices(
            devices,
            showHiddenDevices = false,
            currentTimeMillis = 80_000L,
            offlineGracePeriodMs = 60_000L,
        )
        assertTrue(result.online.isEmpty())
        assertTrue(result.offline.any { it.device.macAddress == "A" })
    }
}
