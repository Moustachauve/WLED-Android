package ca.cgagnier.wlednativeandroid.widget

import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.WifiSignalLevel
import ca.cgagnier.wlednativeandroid.ui.components.toIconRes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

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
        assertEquals(WifiSignalLevel.GOOD, data.signalLevel) // default when online
    }

    @Test
    fun `WidgetStateData defaults signalLevel to OFFLINE when isOnline is false`() {
        val data = WidgetStateData(
            macAddress = "AABBCCDDEEFF",
            address = "192.168.1.100",
            name = "Test Device",
            isOn = false,
            isOnline = false,
        )

        assertEquals(WifiSignalLevel.OFFLINE, data.signalLevel)
    }

    @ParameterizedTest
    @MethodSource("signalLevelIconCases")
    fun `WifiSignalLevel toIconRes maps each level to its drawable`(level: WifiSignalLevel, expectedRes: Int) {
        assertEquals(expectedRes, level.toIconRes())
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

    companion object {
        @JvmStatic
        fun signalLevelIconCases(): Stream<Arguments> = Stream.of(
            Arguments.of(WifiSignalLevel.OFFLINE, R.drawable.twotone_signal_wifi_off_24),
            Arguments.of(WifiSignalLevel.GOOD, R.drawable.twotone_signal_wifi_3_bar_24),
            Arguments.of(WifiSignalLevel.FAIR, R.drawable.twotone_signal_wifi_2_bar_24),
            Arguments.of(WifiSignalLevel.WEAK, R.drawable.twotone_signal_wifi_1_bar_24),
            Arguments.of(WifiSignalLevel.NONE, R.drawable.twotone_signal_wifi_0_bar_24),
        )
    }
}
