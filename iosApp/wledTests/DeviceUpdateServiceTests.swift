import Testing
@testable import WLED
@MainActor
struct DeviceUpdateServiceTests {

    @Test func determineAsset_OlderVersion_NoOverride() {
        // Version older than 0.16.0, should keep raw release
        let targetVersion = "0.15.0"
        
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32_V4", targetVersion: targetVersion) == "ESP32_V4")
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32", targetVersion: targetVersion) == "ESP32")
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP8266", targetVersion: targetVersion) == "ESP8266")
    }

    @Test func determineAsset_NewVersion_WithOverride() {
        // Version 0.16.0+ where dictionary mapping applies
        let targetVersion = "0.16.0"
        
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32_V4", targetVersion: targetVersion) == "ESP32")
        #expect(DeviceUpdateService.determineAsset(byRelease: "esp32_v4", targetVersion: targetVersion) == "ESP32")
    }
    
    @Test func determineAsset_NewVersion_NoOverride() {
        // Version 0.16.0+ but no dictionary mapping applies
        let targetVersion = "0.16.0"
        
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32_S2", targetVersion: targetVersion) == "ESP32_S2")
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP8266", targetVersion: targetVersion) == "ESP8266")
    }

    @Test func determineAsset_PreRelease_Threshold() {
        // Pre-release testing around the 0.16.0 boundary
        let targetVersionBeta = "0.16.0-b2"
        let targetVersionOlderBeta = "0.15.0-b5"
        
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32_V4", targetVersion: targetVersionOlderBeta) == "ESP32_V4")
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32_V4", targetVersion: targetVersionBeta) == "ESP32")
    }
    
    @Test func determineAsset_InvalidVersion() {
        // Invalid semantic version structure, should throw warning log and fallback to raw release
        let invalidVersion = "invalid_version_string"
        
        #expect(DeviceUpdateService.determineAsset(byRelease: "ESP32_V4", targetVersion: invalidVersion) == "ESP32_V4")
    }
}
