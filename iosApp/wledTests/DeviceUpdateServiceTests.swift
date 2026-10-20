import Testing
import Shared

struct DeviceUpdateServiceTests {
    struct OverrideCase: CustomTestStringConvertible, Sendable {
        let rawRelease: String
        let targetVersionTag: String
        let expected: String

        var testDescription: String {
            "\(rawRelease) @ \(targetVersionTag) -> \(expected)"
        }
    }

    @Test(arguments: [
        OverrideCase(rawRelease: "ESP32_V4", targetVersionTag: "0.15.0", expected: "ESP32_V4"),
        OverrideCase(rawRelease: "ESP32", targetVersionTag: "0.15.0", expected: "ESP32"),
        OverrideCase(rawRelease: "ESP8266", targetVersionTag: "0.15.0", expected: "ESP8266"),
        OverrideCase(rawRelease: "ESP32_V4", targetVersionTag: "0.16.0", expected: "ESP32"),
        OverrideCase(rawRelease: "esp32_v4", targetVersionTag: "0.16.0", expected: "ESP32"),
        OverrideCase(rawRelease: "ESP32_V4", targetVersionTag: "0.16.0+build", expected: "ESP32"),
        OverrideCase(rawRelease: "ESP32_S2", targetVersionTag: "0.16.0", expected: "ESP32_S2"),
        OverrideCase(rawRelease: "ESP8266", targetVersionTag: "0.16.0", expected: "ESP8266"),
        OverrideCase(rawRelease: "ESP32_V4", targetVersionTag: "0.15.0-b5", expected: "ESP32_V4"),
        OverrideCase(rawRelease: "ESP32_V4", targetVersionTag: "0.16.0-b2", expected: "ESP32"),
        OverrideCase(rawRelease: "ESP32_V4", targetVersionTag: "invalid_version_string", expected: "ESP32_V4")
    ])
    func resolveReleaseOverride(testCase: OverrideCase) {
        let actual = DeviceUpdateService.companion.resolveReleaseOverride(
            rawRelease: testCase.rawRelease,
            targetVersionTag: testCase.targetVersionTag
        )
        #expect(actual == testCase.expected)
    }
}
