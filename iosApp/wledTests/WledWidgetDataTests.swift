import Foundation
import Shared
import Testing
@testable import WLED

struct WledWidgetDataTests {

    @Test func deepLinkURLUsesCanonicalDevicePath() {
        let data = WledWidgetData(
            macAddress: "aabbccddeeff",
            address: "192.168.1.101",
            name: "Desk Lamp",
            isOn: true,
            lastUpdated: 0
        )

        #expect(data.deepLinkURL?.absoluteString == "wled://device/aabbccddeeff")
    }

    @Test(arguments: [
        (WifiSignalLevel.good, "wifi", 1.0),
        (WifiSignalLevel.fair, "wifi", 0.64),
        (WifiSignalLevel.weak, "wifi", 0.33),
        (WifiSignalLevel.none, "wifi", 0.0),
        (WifiSignalLevel.offline, "wifi.slash", 0.0)
    ])
    func wifiSignalLevelMapsToSymbolNameAndVariableValue(
        level: WifiSignalLevel,
        expectedSymbol: String,
        expectedValue: Double
    ) {
        #expect(level.symbolName == expectedSymbol)
        #expect(level.variableValue == expectedValue)
    }

    @Test func decodingLegacyJsonWithoutSignalLevelDefaultsBasedOnOnlineStatus() throws {
        let legacyJson = Data("""
        {
            "macAddress": "aabbccddeeff",
            "address": "192.168.1.101",
            "name": "Desk Lamp",
            "isOn": true,
            "isOnline": true,
            "color": -1,
            "lastUpdated": 1000
        }
        """.utf8)

        let decoded = try JSONDecoder().decode(WledWidgetData.self, from: legacyJson)
        #expect(decoded.signalLevel == .good)
    }

    struct DeepLinkCase: Sendable {
        let urlString: String
        let expectedMac: String?
    }

    @Test(arguments: [
        DeepLinkCase(urlString: "wled://device/aabbccddeeff", expectedMac: "aabbccddeeff"),
        DeepLinkCase(urlString: "WLED://DEVICE/aabbccddeeff", expectedMac: "aabbccddeeff"),
        DeepLinkCase(urlString: "wled://device/", expectedMac: nil),
        DeepLinkCase(urlString: "wled://device/aabbccddeeff/extra", expectedMac: nil),
        DeepLinkCase(urlString: "wled://other/aabbccddeeff", expectedMac: nil),
        DeepLinkCase(urlString: "https://device/aabbccddeeff", expectedMac: nil)
    ])
    func parseDeepLinkMacAddressAcceptsOnlyCanonicalDeviceURLs(_ testCase: DeepLinkCase) throws {
        let url = try #require(URL(string: testCase.urlString))
        #expect(WledWidgetData.parseDeepLinkMacAddress(url) == testCase.expectedMac)
    }
}
