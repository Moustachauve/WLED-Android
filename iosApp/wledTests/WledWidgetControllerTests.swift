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

    @Test func setPowerPostsDesiredStateAndUpdatesSnapshot() async throws {
        try await insertDevice()
        stubState(isOn: false, brightness: 128)

        let snapshot = await controller.setPower(macAddress: "aabbccddeeff", isOn: false)

        let requests = StubURLProtocol.requests(for: host)
        #expect(requests.count == 1)
        let payload = try decodeJsonBody(from: requests.first)
        #expect(payload["on"] as? Bool == false)
        #expect(payload["v"] as? Bool == true)
        #expect(snapshot?.isOnline == true)
        #expect(snapshot?.isOn == false)
    }

    @Test func setBrightnessConvertsPercentageToWledRange() async throws {
        try await insertDevice()
        stubState(isOn: true, brightness: 127)

        let snapshot = await controller.setBrightness(macAddress: "aabbccddeeff", percent: 50)

        let requests = StubURLProtocol.requests(for: host)
        #expect(requests.count == 1)
        let payload = try decodeJsonBody(from: requests.first)
        #expect(payload["on"] as? Bool == true)
        #expect(payload["bri"] as? Int == 127)
        #expect(snapshot?.isOnline == true)
        #expect(snapshot?.isOn == true)
        #expect(snapshot?.brightness?.intValue == 127)
    }

    @Test func togglePowerSkipsSetPowerWhenDeviceIsOffline() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.togglePower(macAddress: "aabbccddeeff")

        #expect(StubURLProtocol.requests(for: host).count == 1)
        #expect(snapshot?.isOnline == false)
    }

    @Test func widgetDataDeepLinkUsesMacAddressPath() {
        let data = WledWidgetData(
            macAddress: "AA:BB:CC:DD:EE:01",
            address: "192.168.1.101",
            name: "Desk Lamp",
            isOn: true,
            isOnline: true,
            color: -1,
            lastUpdated: 0
        )

        #expect(data.deepLinkURL?.absoluteString == "wled://device/AA:BB:CC:DD:EE:01")
    }

    private func decodeJsonBody(from request: URLRequest?) throws -> [String: Any] {
        guard let body = request?.httpBody,
              let json = try JSONSerialization.jsonObject(with: body) as? [String: Any] else {
            return [:]
        }
        return json
    }
}
