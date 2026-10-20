import Testing
import Shared
@testable import WLED

@MainActor
struct DeviceUpdateServiceTests {

    @Test func determineAssetOlderVersionNoOverride() {
        let targetVersion = "0.15.0"

        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32_V4",
                targetVersionTag: targetVersion
            ) == "ESP32_V4"
        )
        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32",
                targetVersionTag: targetVersion
            ) == "ESP32"
        )
        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP8266",
                targetVersionTag: targetVersion
            ) == "ESP8266"
        )
    }

    @Test func determineAssetNewVersionWithOverride() {
        let targetVersion = "0.16.0"

        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32_V4",
                targetVersionTag: targetVersion
            ) == "ESP32"
        )
        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "esp32_v4",
                targetVersionTag: targetVersion
            ) == "ESP32"
        )
    }

    @Test func determineAssetNewVersionNoOverride() {
        let targetVersion = "0.16.0"

        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32_S2",
                targetVersionTag: targetVersion
            ) == "ESP32_S2"
        )
        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP8266",
                targetVersionTag: targetVersion
            ) == "ESP8266"
        )
    }

    @Test func determineAssetPreReleaseThreshold() {
        let targetVersionBeta = "0.16.0-b2"
        let targetVersionOlderBeta = "0.15.0-b5"

        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32_V4",
                targetVersionTag: targetVersionOlderBeta
            ) == "ESP32_V4"
        )
        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32_V4",
                targetVersionTag: targetVersionBeta
            ) == "ESP32"
        )
    }

    @Test func determineAssetInvalidVersion() {
        let invalidVersion = "invalid_version_string"

        #expect(
            DeviceUpdateService.companion.resolveReleaseOverride(
                rawRelease: "ESP32_V4",
                targetVersionTag: invalidVersion
            ) == "ESP32_V4"
        )
    }
}
