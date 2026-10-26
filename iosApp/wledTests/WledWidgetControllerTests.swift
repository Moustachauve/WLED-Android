import Foundation
import Shared
import Testing
@testable import WLED

struct WledWidgetControllerTests {

    private static let apiFactory = DeviceApiFactory.companion.create { configuration in
        configuration.protocolClasses = [StubURLProtocol.self]
    }

    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let database = AppDatabase(inMemory: true, deviceApiFactory: Self.apiFactory)
    var repository: DeviceRepository { database.deviceRepository }
    var controller: WledWidgetController { WledWidgetController(database: database) }

    private func insertDevice(mac: String = "aabbccddeeff", customName: String = "Desk Lamp") async throws {
        await database.waitUntilReady()
        let device = Device(
            macAddress: mac,
            address: host,
            originalName: "WLED",
            customName: customName
        )
        try await repository.insert(device: device)
    }

    private func stubState(isOn: Bool, brightness: Int = 180, rgb: [Int] = [0, 191, 255]) {
        let body = """
        {
          "on": \(isOn),
          "bri": \(brightness),
          "seg": [{"col": [[\(rgb[0]), \(rgb[1]), \(rgb[2])]]}]
        }
        """
        StubURLProtocol.register(.http(statusCode: 200, body: body), for: host)
    }

    @Test func refreshDeviceReturnsOnlineSnapshotWithPrimaryColor() async throws {
        try await insertDevice()
        stubState(isOn: true, brightness: 200, rgb: [0, 191, 255])

        let snapshot = await controller.refreshDevice(macAddress: "aabbccddeeff")

        #expect(snapshot?.name == "Desk Lamp")
        #expect(snapshot?.address == host)
        #expect(snapshot?.isOnline == true)
        #expect(snapshot?.isOn == true)
        #expect(snapshot?.brightness?.intValue == 200)
        #expect(snapshot?.color == Int32(bitPattern: 0xFF00BFFF))
    }

    @Test func refreshDeviceMarksSnapshotOfflineOnFailure() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.refreshDevice(
            macAddress: "aabbccddeeff",
            fallbackIsOn: true,
            fallbackColor: Int32(bitPattern: 0xFFFF8000)
        )

        #expect(snapshot?.isOnline == false)
        #expect(snapshot?.isOn == true)
        #expect(snapshot?.color == Int32(bitPattern: 0xFFFF8000))
    }

    @Test func refreshDeviceReturnsNilWhenDeviceNotFoundAndNoFallbackAddress() async {
        let snapshot = await controller.refreshDevice(macAddress: "aabbccddeeff")

        #expect(snapshot == nil)
    }

    @Test func refreshDevicePreservesMacAddressWhenUsingFallbackAddress() async {
        stubState(isOn: true, brightness: 180)

        let snapshot = await controller.refreshDevice(
            macAddress: "aabbccddeeff",
            fallbackAddress: host
        )

        #expect(snapshot?.macAddress == "aabbccddeeff")
        #expect(snapshot?.address == host)
        #expect(snapshot?.isOnline == true)
    }

    @Test func setPowerPostsDesiredState() async throws {
        try await insertDevice()
        stubState(isOn: false, brightness: 128)

        _ = await controller.setPower(macAddress: "aabbccddeeff", isOn: false)

        let requests = StubURLProtocol.requests(for: host)
        #expect(requests.count == 1)
        let payload = try decodeJsonBody(from: requests.first)
        #expect(payload["on"] as? Bool == false)
        #expect(payload["v"] as? Bool == true)
    }

    @Test func setPowerDoesNotReportRequestedStateWhenRequestFails() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.setPower(macAddress: "aabbccddeeff", isOn: true)

        #expect(snapshot?.isOnline == false)
        #expect(snapshot?.isOn == false)
    }

    struct BrightnessCase: Sendable {
        let percent: Int
        let expectedBri: Int
        let expectedOn: Bool
    }

    @Test(arguments: [
        BrightnessCase(percent: 0, expectedBri: 0, expectedOn: false),
        BrightnessCase(percent: 50, expectedBri: 127, expectedOn: true),
        BrightnessCase(percent: 100, expectedBri: 255, expectedOn: true)
    ])
    func setBrightnessConvertsPercentageToWledRange(_ testCase: BrightnessCase) async throws {
        try await insertDevice()
        stubState(isOn: testCase.expectedOn, brightness: testCase.expectedBri)

        _ = await controller.setBrightness(macAddress: "aabbccddeeff", percent: testCase.percent)

        let requests = StubURLProtocol.requests(for: host)
        #expect(requests.count == 1)
        let payload = try decodeJsonBody(from: requests.first)
        #expect(payload["on"] as? Bool == testCase.expectedOn)
        #expect(payload["bri"] as? Int == testCase.expectedBri)
    }

    @Test func togglePowerPostsInvertedStateWhenDeviceIsOnline() async throws {
        try await insertDevice()
        stubState(isOn: true, brightness: 180)

        _ = await controller.togglePower(macAddress: "aabbccddeeff")

        let requests = StubURLProtocol.requests(for: host)
        #expect(requests.count == 2)
        let secondPayload = try decodeJsonBody(from: requests.last)
        #expect(secondPayload["on"] as? Bool == false)
    }

    @Test func togglePowerSkipsSetPowerWhenDeviceIsOffline() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.togglePower(macAddress: "aabbccddeeff")

        #expect(StubURLProtocol.requests(for: host).count == 1)
        #expect(snapshot?.isOnline == false)
    }

    private func decodeJsonBody(from request: URLRequest?) throws -> [String: Any] {
        let body = try #require(request?.httpBody)
        return try #require(try JSONSerialization.jsonObject(with: body) as? [String: Any])
    }
}
