package ca.cgagnier.wlednativeandroid.model.wledapi

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val SAMPLE_DEVICE_STATE_INFO_JSON = """
    {
        "state": {
            "on": true,
            "bri": 128,
            "transition": 7,
            "ps": 1,
            "pl": -1,
            "nl": {
                "on": false,
                "dur": 60,
                "fade": true,
                "mode": 1,
                "tbri": 0,
                "rem": -1
            },
            "mainseg": 0,
            "seg": [
                {
                    "id": 0,
                    "start": 0,
                    "stop": 30,
                    "len": 30,
                    "grp": 1,
                    "spc": 0,
                    "on": true,
                    "bri": 255,
                    "col": [[255, 160, 0], [0, 0, 0]],
                    "fx": 0,
                    "sx": 128,
                    "ix": 128,
                    "pal": 0,
                    "sel": true,
                    "rev": false,
                    "mi": false
                }
            ]
        },
        "info": {
            "ver": "0.14.0",
            "vid": 2310130,
            "name": "Living Room LED",
            "udpport": 21324,
            "live": false,
            "fxcount": 187,
            "palcount": 71,
            "opt": 1,
            "arch": "esp32",
            "core": "v3.3.5-1-g850c099",
            "freeheap": 145000,
            "uptime": 3600,
            "brand": "WLED",
            "product": "Fargbot",
            "mac": "a0b1c2d3e4f5",
            "ip": "192.168.1.100",
            "leds": {
                "count": 30,
                "pwr": 450,
                "fps": 42,
                "maxpwr": 850,
                "maxseg": 16
            },
            "wifi": {
                "bssid": "aa:bb:cc:dd:ee:ff",
                "rssi": -60,
                "signal": 80,
                "channel": 11,
                "ap": false
            },
            "fs": {
                "u": 64,
                "t": 1024,
                "pmt": 1699999999
            }
        }
    }
""".trimIndent()

private val SAMPLE_UNKNOWN_FIELDS_INFO_JSON = """
    {
        "name": "Test LED",
        "ver": "0.15.0",
        "future_field_not_yet_known": "hello",
        "another_extra_number": 42,
        "leds": { "count": 100 },
        "wifi": { "rssi": -55 }
    }
""".trimIndent()

class WledApiSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `test DeviceStateInfo full deserialization`() {
        val deviceStateInfo = json.decodeFromString<DeviceStateInfo>(SAMPLE_DEVICE_STATE_INFO_JSON)

        // Verify State
        assertEquals(true, deviceStateInfo.state.isOn)
        assertEquals(128, deviceStateInfo.state.brightness)
        assertEquals(7, deviceStateInfo.state.transition)
        assertEquals(1, deviceStateInfo.state.selectedPresetId)
        assertNotNull(deviceStateInfo.state.nightlight)
        assertEquals(false, deviceStateInfo.state.nightlight?.isOn)
        assertEquals(1, deviceStateInfo.state.segment?.size)

        val segment = deviceStateInfo.state.segment?.first()
        assertNotNull(segment)
        assertEquals(0, segment?.id)
        assertEquals(30, segment?.length)
        assertEquals(listOf(listOf(255, 160, 0), listOf(0, 0, 0)), segment?.colors)

        // Verify Info
        assertEquals("0.14.0", deviceStateInfo.info.version)
        assertEquals(2310130, deviceStateInfo.info.buildId)
        assertEquals("Living Room LED", deviceStateInfo.info.name)
        assertEquals("esp32", deviceStateInfo.info.platformName)
        assertEquals("a0b1c2d3e4f5", deviceStateInfo.info.macAddress)
        assertEquals(30, deviceStateInfo.info.leds.count)
        assertEquals(42, deviceStateInfo.info.leds.fps)
        assertEquals(-60, deviceStateInfo.info.wifi.rssi)
        assertEquals(80, deviceStateInfo.info.wifi.signal)
        assertFalse(deviceStateInfo.info.wifi.isApMode ?: true)
        assertEquals(64, deviceStateInfo.info.fileSystem?.spaceUsed)
        assertTrue(deviceStateInfo.info.isOtaEnabled)
    }

    @Test
    fun `test Info with unknown fields ignores extra keys`() {
        val info = json.decodeFromString<Info>(SAMPLE_UNKNOWN_FIELDS_INFO_JSON)
        assertEquals("Test LED", info.name)
        assertEquals("0.15.0", info.version)
        assertEquals(100, info.leds.count)
        assertEquals(-55, info.wifi.rssi)
    }

    @Test
    fun `test State serialization omits null fields`() {
        val state = State(
            isOn = true,
            brightness = 255,
        )

        val serialized = json.encodeToString(state)
        assertTrue(serialized.contains(""""on":true"""))
        assertTrue(serialized.contains(""""bri":255"""))
        assertFalse(serialized.contains(""""transition""""))
        assertFalse(serialized.contains(""""ps""""))
        assertFalse(serialized.contains(""""seg""""))
    }

    @Test
    fun `test Info isOtaEnabled computation`() {
        val infoWithOta = Info(
            name = "Device 1",
            options = 1,
            leds = Leds(),
            wifi = Wifi(),
        )
        assertTrue(infoWithOta.isOtaEnabled)

        val infoWithoutOta = Info(
            name = "Device 2",
            options = 0,
            leds = Leds(),
            wifi = Wifi(),
        )
        assertFalse(infoWithoutOta.isOtaEnabled)

        val infoNullOptions = Info(
            name = "Device 3",
            options = null,
            leds = Leds(),
            wifi = Wifi(),
        )
        assertTrue(infoNullOptions.isOtaEnabled)
    }
}
