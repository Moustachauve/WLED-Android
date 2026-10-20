import Testing
import Shared

struct DeviceUpdateServiceTests {
    @Test(arguments: [
        ("ESP32_V4", "0.15.0", "ESP32_V4"),
        ("ESP32", "0.15.0", "ESP32"),
        ("ESP8266", "0.15.0", "ESP8266"),
        ("ESP32_V4", "0.16.0", "ESP32"),
        ("esp32_v4", "0.16.0", "ESP32"),
        ("ESP32_S2", "0.16.0", "ESP32_S2"),
        ("ESP8266", "0.16.0", "ESP8266"),
        ("ESP32_V4", "0.15.0-b5", "ESP32_V4"),
        ("ESP32_V4", "0.16.0-b2", "ESP32"),
        ("ESP32_V4", "invalid_version_string", "ESP32_V4")
    ])
    func resolveReleaseOverride(rawRelease: String, targetVersionTag: String, expected: String) {
        let actual = DeviceUpdateService.companion.resolveReleaseOverride(
            rawRelease: rawRelease,
            targetVersionTag: targetVersionTag
        )
        #expect(actual == expected)
    }
}
