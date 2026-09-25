package ca.cgagnier.wlednativeandroid.model.wledapi

import com.diffplug.selfie.Selfie.expectSelfie
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
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

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `test DeviceStateInfo full deserialization with real WLED 16_0_1 data`() {
        val deviceStateInfo = json.decodeFromString<DeviceStateInfo>(SAMPLE_DEVICE_STATE_INFO_JSON)
        expectSelfie(prettyJson.encodeToString(deviceStateInfo)).toMatchDisk()
    }

    @Test
    fun `test Info with unknown fields ignores extra keys`() {
        val info = json.decodeFromString<Info>(SAMPLE_UNKNOWN_FIELDS_INFO_JSON)
        expectSelfie(prettyJson.encodeToString(info)).toBe(
            """{
  "leds": {
    "count": 100
  },
  "wifi": {
    "rssi": -55
  },
  "ver": "16.0.1",
  "name": "Test LED"
}""",
        )
    }

    @Test
    fun `test State serialization omits null fields`() {
        val state = State(
            isOn = true,
            brightness = 255,
        )

        val serialized = json.encodeToString(state)
        expectSelfie(serialized).toBe("{\"on\":true,\"bri\":255}")
    }

    @Test
    fun `test State with multiple segments and nightlight serialization`() {
        val state = State(
            isOn = true,
            brightness = 195,
            transition = 7,
            nightlight = Nightlight(isOn = false, duration = 60, mode = 1, targetBrightness = 0, remainingTime = -1),
            segment = listOf(
                Segment(
                    id = 0,
                    start = 0,
                    stop = 88,
                    length = 88,
                    grouping = 1,
                    spacing = 0,
                    isOn = true,
                    brightness = 255,
                    colors = listOf(listOf(0, 17, 255, 0), listOf(144, 79, 255, 0)),
                    effect = 107,
                    effectSpeed = 20,
                    effectIntensity = 144,
                    palette = 3,
                ),
            ),
        )
        val serialized = prettyJson.encodeToString(state)
        expectSelfie(serialized).toMatchDisk()
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
