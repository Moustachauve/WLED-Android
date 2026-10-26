import Foundation
import Shared
import Testing
@testable import WLED

struct WledWidgetControllerTests {

    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let mac = "aabbccddeeff"
    let database = AppDatabase(inMemory: true, deviceApiFactory: .stubbed)
    var repository: DeviceRepository { database.deviceRepository }
    var controller: WledWidgetController { WledWidgetController(database: database) }

    private func insertDevice(customName: String = "Desk Lamp") async throws {
        await database.waitUntilReady()
        let device = Device(
            macAddress: mac,
            address: host,
            originalName: "WLED",
            customName: customName
        )
        try await repository.insert(device: device)
    }

    private func stubState(isOn: Bool, brightness: Int = 180) {
        let body = """
        {
          "on": \(isOn),
          "bri": \(brightness),
          "seg": [{"col": [[0, 191, 255]]}]
        }
        """
        StubURLProtocol.register(.http(statusCode: 200, body: body), for: host)
    }

    private func postedPayloads() throws -> [[String: Any]] {
        try StubURLProtocol.requests(for: host).map { request in
            let body = try #require(request.httpBody)
            return try #require(try JSONSerialization.jsonObject(with: body) as? [String: Any])
        }
    }

    @Test func refreshDeviceReturnsOnlineSnapshot() async throws {
        try await insertDevice()
        stubState(isOn: true, brightness: 200)

        let snapshot = await controller.refreshDevice(macAddress: mac)

        #expect(snapshot?.address == host)
        #expect(snapshot?.isOnline == true)
        #expect(snapshot?.isOn == true)
        #expect(snapshot?.brightness?.intValue == 200)
    }

    @Test func refreshDeviceMarksSnapshotOfflineOnFailure() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.refreshDevice(macAddress: mac)

        #expect(snapshot?.isOnline == false)
        #expect(snapshot?.isOn == false)
    }

    @Test func refreshDeviceReturnsNilWhenDeviceNotFoundAndNoFallbackAddress() async {
        let snapshot = await controller.refreshDevice(macAddress: mac)

        #expect(snapshot == nil)
    }

    @Test func refreshDevicePreservesMacAddressWhenUsingFallbackAddress() async {
        stubState(isOn: true)

        let snapshot = await controller.refreshDevice(
            macAddress: mac,
            fallbackAddress: host
        )

        #expect(snapshot?.macAddress == mac)
        #expect(snapshot?.address == host)
        #expect(snapshot?.isOnline == true)
    }

    @Test func setPowerPostsDesiredState() async throws {
        try await insertDevice()
        stubState(isOn: false, brightness: 128)

        _ = await controller.setPower(macAddress: mac, isOn: false)

        let payloads = try postedPayloads()
        #expect(payloads.count == 1)
        #expect(payloads.first?["on"] as? Bool == false)
        #expect(payloads.first?["v"] as? Bool == true)
    }

    @Test func setPowerDoesNotReportRequestedStateWhenRequestFails() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.setPower(macAddress: mac, isOn: true)

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

        _ = await controller.setBrightness(macAddress: mac, percent: testCase.percent)

        let payloads = try postedPayloads()
        #expect(payloads.count == 1)
        #expect(payloads.first?["on"] as? Bool == testCase.expectedOn)
        #expect(payloads.first?["bri"] as? Int == testCase.expectedBri)
    }

    @Test func togglePowerPostsInvertedStateWhenDeviceIsOnline() async throws {
        try await insertDevice()
        stubState(isOn: true)

        _ = await controller.togglePower(macAddress: mac)

        let payloads = try postedPayloads()
        #expect(payloads.count == 2)
        #expect(payloads.last?["on"] as? Bool == false)
    }

    @Test func togglePowerSkipsSetPowerWhenDeviceIsOffline() async throws {
        try await insertDevice()
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let snapshot = await controller.togglePower(macAddress: mac)

        #expect(StubURLProtocol.requests(for: host).count == 1)
        #expect(snapshot?.isOnline == false)
    }
}
