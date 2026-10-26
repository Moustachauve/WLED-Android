import Foundation
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
