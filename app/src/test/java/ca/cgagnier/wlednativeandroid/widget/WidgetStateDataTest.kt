package ca.cgagnier.wlednativeandroid.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetStateDataTest {

    @Test
    fun `WidgetStateData defaults are correct`() {
        val data = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test Device",
            isOn = true,
        )

        assertEquals("AABBCCDDEEFF", data.macAddress)
        assertEquals("192.168.1.100", data.address)
        assertEquals("Test Device", data.name)
        assertTrue(data.isOn)
        assertTrue(data.isOnline) // default
        assertEquals(-1, data.color) // default
    }

    @Test
    fun `WidgetStateData with custom values`() {
        val data = WidgetStateData(
            macAddress = "112233445566",
            address = "10.0.0.50",
            name = "Living Room",
            isOn = false,
            isOnline = false,
            color = 0xFF0000FF.toInt(),
            batteryLevel = 85,
            lastUpdated = 1234567890L,
        )

        assertEquals("112233445566", data.macAddress)
        assertEquals("10.0.0.50", data.address)
        assertEquals("Living Room", data.name)
        assertFalse(data.isOn)
        assertFalse(data.isOnline)
        assertEquals(0xFF0000FF.toInt(), data.color)
        assertEquals(85, data.batteryLevel)
        assertEquals(1234567890L, data.lastUpdated)
    }

    @Test
    fun `WidgetStateData copy preserves unchanged fields`() {
        val original = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test Device",
            isOn = true,
            color = 0xFFFF0000.toInt(),
        )

        val copied = original.copy(isOn = false, isOnline = false)

        assertEquals(original.macAddress, copied.macAddress)
        assertEquals(original.address, copied.address)
        assertEquals(original.name, copied.name)
        assertEquals(original.color, copied.color)
        assertFalse(copied.isOn)
        assertFalse(copied.isOnline)
    }

    @Test
    fun `WidgetStateData lastUpdatedFormatted returns formatted date`() {
        val data = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test Device",
            isOn = true,
            lastUpdated = 1704067200000L, // Jan 1, 2024 00:00:00 UTC
        )

        // Verify it returns a non-empty formatted string
        assertTrue(data.lastUpdatedFormatted.isNotBlank())
    }

    @Test
    fun `WidgetStateData equality works correctly`() {
        val data1 = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test",
            isOn = true,
            lastUpdated = 1000L,
        )
        val data2 = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test",
            isOn = true,
            lastUpdated = 1000L,
        )

        assertEquals(data1, data2)
    }

    @Test
    fun `WidgetStateData different MAC addresses are not equal`() {
        val data1 = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test",
            isOn = true,
        )
        val data2 = WidgetStateData(
            macAddress = "112233445566",
            address = "192.168.1.100",
            name = "Test",
            isOn = true,
        )

        assertTrue(data1 != data2)
    }
}
