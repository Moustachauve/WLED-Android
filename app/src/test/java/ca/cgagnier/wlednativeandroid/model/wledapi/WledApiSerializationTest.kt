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
            "bri": 195,
            "transition": 7,
            "ps": -1,
            "pl": -1,
            "nl": {
                "on": false,
                "dur": 60,
                "mode": 1,
                "tbri": 0,
                "rem": -1
            },
            "lor": 0,
            "mainseg": 0,
            "seg": [
                {
                    "id": 0,
                    "start": 0,
                    "stop": 88,
                    "len": 88,
                    "grp": 1,
                    "spc": 0,
                    "on": true,
                    "bri": 255,
                    "col": [[0, 17, 255, 0], [144, 79, 255, 0], [0, 0, 0, 0]],
                    "fx": 107,
                    "sx": 20,
                    "ix": 144,
                    "pal": 3,
                    "sel": false,
                    "rev": false,
                    "mi": false
                },
                {
                    "id": 1,
                    "start": 88,
                    "stop": 177,
                    "len": 89,
                    "grp": 1,
                    "spc": 0,
                    "on": true,
                    "bri": 255,
                    "col": [[99, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]],
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
            "ver": "16.0.1",
            "vid": 2606300,
            "cn": "Niji",
            "release": "ESP32",
            "repo": "wled/WLED",
            "name": "WLED Desk",
            "udpport": 21324,
            "simplifiedui": false,
            "live": false,
            "liveseg": -1,
            "ws": 3,
            "fxcount": 220,
            "palcount": 73,
            "cpalcount": 1,
            "arch": "esp32",
            "core": "4.4.8.240628",
            "clock": 240,
            "flash": 4,
            "freeheap": 120932,
            "uptime": 2252733,
            "time": "2026-9-22, 23:30:48",
            "opt": 79,
            "brand": "WLED",
            "product": "FOSS",
            "mac": "aabbccddeeff",
            "ip": "192.168.1.100",
            "leds": {
                "count": 277,
                "pwr": 2171,
                "fps": 43,
                "maxpwr": 10002,
                "maxseg": 32
            },
            "wifi": {
                "bssid": "aa:bb:cc:dd:ee:ff",
                "rssi": -72,
                "signal": 56,
                "channel": 1,
                "ap": false
            },
            "fs": {
                "u": 32,
                "t": 983,
                "pmt": 1788492069
            }
        }
    }
""".trimIndent()

private val SAMPLE_UNKNOWN_FIELDS_INFO_JSON = """
    {
        "name": "Test LED",
        "ver": "16.0.1",
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
    fun `test DeviceStateInfo full deserialization with real WLED 16_0_1 data`() {
        val deviceStateInfo = json.decodeFromString<DeviceStateInfo>(SAMPLE_DEVICE_STATE_INFO_JSON)

        // Verify State
        assertEquals(true, deviceStateInfo.state.isOn)
        assertEquals(195, deviceStateInfo.state.brightness)
        assertEquals(7, deviceStateInfo.state.transition)
        assertEquals(-1, deviceStateInfo.state.selectedPresetId)
        assertNotNull(deviceStateInfo.state.nightlight)
        assertEquals(false, deviceStateInfo.state.nightlight?.isOn)
        assertEquals(2, deviceStateInfo.state.segment?.size)

        val segment = deviceStateInfo.state.segment?.first()
        assertNotNull(segment)
        assertEquals(0, segment?.id)
        assertEquals(88, segment?.length)
        assertEquals(listOf(listOf(0, 17, 255, 0), listOf(144, 79, 255, 0), listOf(0, 0, 0, 0)), segment?.colors)
        assertEquals(107, segment?.effect)

        // Verify Info
        assertEquals("16.0.1", deviceStateInfo.info.version)
        assertEquals(2606300, deviceStateInfo.info.buildId)
        assertEquals("Niji", deviceStateInfo.info.codeName)
        assertEquals("ESP32", deviceStateInfo.info.release)
        assertEquals("wled/WLED", deviceStateInfo.info.repository)
        assertEquals("WLED Desk", deviceStateInfo.info.name)
        assertEquals("esp32", deviceStateInfo.info.platformName)
        assertEquals("4.4.8.240628", deviceStateInfo.info.arduinoCoreVersion)
        assertEquals(240, deviceStateInfo.info.clockFrequency)
        assertEquals(4, deviceStateInfo.info.flashChipSize)
        assertEquals("FOSS", deviceStateInfo.info.product)
        assertEquals("aabbccddeeff", deviceStateInfo.info.macAddress)
        assertEquals("192.168.1.100", deviceStateInfo.info.ipAddress)
        assertEquals(277, deviceStateInfo.info.leds.count)
        assertEquals(43, deviceStateInfo.info.leds.fps)
        assertEquals(-72, deviceStateInfo.info.wifi.rssi)
        assertEquals(56, deviceStateInfo.info.wifi.signal)
        assertFalse(deviceStateInfo.info.wifi.isApMode ?: true)
        assertEquals(32, deviceStateInfo.info.fileSystem?.spaceUsed)
        assertEquals(983, deviceStateInfo.info.fileSystem?.spaceTotal)
        assertTrue(deviceStateInfo.info.isOtaEnabled)
    }

    @Test
    fun `test Info with unknown fields ignores extra keys`() {
        val info = json.decodeFromString<Info>(SAMPLE_UNKNOWN_FIELDS_INFO_JSON)
        assertEquals("Test LED", info.name)
        assertEquals("16.0.1", info.version)
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
