import Testing
import Foundation
import Shared
@testable import WLED

/// Exercises `DeviceFirstContactService` end-to-end through the shared Kotlin `DeviceApi`
/// and the Ktor Darwin engine, with HTTP responses served by `StubURLProtocol`.
struct DeviceFirstContactServiceTests {

    private static let factory = DeviceApiFactory.companion.create { configuration in
        configuration.protocolClasses = [StubURLProtocol.self]
    }

    /// Unique per test so stubs never collide when tests run in parallel.
    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let database = AppDatabase(inMemory: true)
    var repository: DeviceRepository { database.deviceRepository }

    private func makeService() -> DeviceFirstContactService {
        DeviceFirstContactService(repository: repository, deviceApiFactory: Self.factory)
    }

    private func stubInfo(name: String = "WLED Office", mac: String? = "aabbccddeeff") {
        let macField = mac.map { #""mac": "\#($0)","# } ?? ""
        let body = #"{ \#(macField) "name": "\#(name)", "ver": "0.15.0", "leds": {}, "wifi": {}, "extra": 1 }"#
        StubURLProtocol.register(.http(statusCode: 200, body: body), for: host)
    }

    private func insertDevice(mac: String = "aabbccddeeff", address: String, name: String = "WLED Office") async throws {
        try await repository.insert(device: Device(macAddress: mac, address: address, originalName: name, lastSeen: 0))
    }

    // MARK: - fetchAndUpsertDevice

    @Test func fetchAndUpsertRequestsJsonInfoOverHttp() async throws {
        stubInfo()

        _ = try await makeService().fetchAndUpsertDevice(rawAddress: host)

        #expect(StubURLProtocol.requests(for: host).map(\.url?.absoluteString) == ["http://\(host)/json/info"])
    }

    @Test func fetchAndUpsertCreatesNewDevice() async throws {
        stubInfo(name: "Kitchen", mac: "001122334455")

        let device = try await makeService().fetchAndUpsertDevice(rawAddress: host)

        let stored = try await repository.findDeviceByMacAddress(address: "001122334455")
        #expect(stored?.address == host)
        #expect(stored?.originalName == "Kitchen")
        #expect(device.macAddress == "001122334455")
    }

    @Test func fetchAndUpsertStripsSchemeAndTrailingSlashes() async throws {
        stubInfo()

        let device = try await makeService().fetchAndUpsertDevice(rawAddress: "http://\(host)//")

        #expect(device.address == host)
    }

    @Test func fetchAndUpsertReturnsExistingDeviceWhenUnchanged() async throws {
        try await insertDevice(address: host)
        stubInfo()

        let device = try await makeService().fetchAndUpsertDevice(rawAddress: host)

        #expect(device.address == host)
        #expect(device.originalName == "WLED Office")
    }

    @Test func fetchAndUpsertUpdatesAddressOfKnownDevice() async throws {
        try await insertDevice(address: "10.0.0.1")
        stubInfo()

        _ = try await makeService().fetchAndUpsertDevice(rawAddress: host)

        let stored = try await repository.findDeviceByMacAddress(address: "aabbccddeeff")
        #expect(stored?.address == host)
    }

    @Test func fetchAndUpsertUpdatesNameOfKnownDevice() async throws {
        try await insertDevice(address: host, name: "Old Name")
        stubInfo(name: "New Name")

        _ = try await makeService().fetchAndUpsertDevice(rawAddress: host)

        let stored = try await repository.findDeviceByMacAddress(address: "aabbccddeeff")
        #expect(stored?.originalName == "New Name")
    }

    @Test func fetchAndUpsertThrowsWhenMacAddressIsMissing() async throws {
        stubInfo(mac: nil)

        await #expect(throws: DeviceFirstContactService.ServiceError.self) {
            try await makeService().fetchAndUpsertDevice(rawAddress: host)
        }
    }

    @Test func fetchAndUpsertThrowsWhenMacAddressIsEmpty() async throws {
        stubInfo(mac: "")

        await #expect(throws: DeviceFirstContactService.ServiceError.self) {
            try await makeService().fetchAndUpsertDevice(rawAddress: host)
        }
    }

    @Test func fetchAndUpsertThrowsHttpErrorOnNonSuccessStatus() async throws {
        StubURLProtocol.register(.http(statusCode: 500, body: "Oops", contentType: "text/plain"), for: host)

        let error = await #expect(throws: DeviceFirstContactService.ServiceError.self) {
            try await makeService().fetchAndUpsertDevice(rawAddress: host)
        }

        guard case .httpError(let statusCode) = error else {
            Issue.record("Expected httpError, got \(String(describing: error))")
            return
        }
        #expect(statusCode == 500)
    }

    /// Verifies Kotlin exceptions surface as Swift errors instead of crashing (`@Throws` on `DeviceApi`).
    @Test func fetchAndUpsertThrowsNetworkErrorWhenDeviceIsUnreachable() async throws {
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        let error = await #expect(throws: DeviceFirstContactService.ServiceError.self) {
            try await makeService().fetchAndUpsertDevice(rawAddress: host)
        }

        guard case .networkError = error else {
            Issue.record("Expected networkError, got \(String(describing: error))")
            return
        }
    }

    @Test func fetchAndUpsertThrowsNetworkErrorOnMalformedJson() async throws {
        StubURLProtocol.register(.http(statusCode: 200, body: "{ not json"), for: host)

        let error = await #expect(throws: DeviceFirstContactService.ServiceError.self) {
            try await makeService().fetchAndUpsertDevice(rawAddress: host)
        }

        guard case .networkError = error else {
            Issue.record("Expected networkError, got \(String(describing: error))")
            return
        }
    }

    @Test func fetchAndUpsertDoesNotStoreDeviceOnFailure() async throws {
        StubURLProtocol.register(.failure(.timedOut), for: host)

        _ = try? await makeService().fetchAndUpsertDevice(rawAddress: host)

        let stored = try await repository.findDeviceByMacAddress(address: "aabbccddeeff")
        #expect(stored == nil)
    }

    // MARK: - tryUpdateAddress

    @Test func tryUpdateAddressReturnsFalseForNilMac() async {
        #expect(await makeService().tryUpdateAddress(macAddress: nil, address: host) == false)
    }

    @Test func tryUpdateAddressReturnsFalseForEmptyMac() async {
        #expect(await makeService().tryUpdateAddress(macAddress: "", address: host) == false)
    }

    @Test func tryUpdateAddressReturnsFalseForUnknownDevice() async {
        #expect(await makeService().tryUpdateAddress(macAddress: "aabbccddeeff", address: host) == false)
    }

    @Test func tryUpdateAddressUpdatesAddressOfKnownDevice() async throws {
        try await insertDevice(address: "10.0.0.1")

        let handled = await makeService().tryUpdateAddress(macAddress: "aabbccddeeff", address: "http://10.0.0.2/")

        let stored = try await repository.findDeviceByMacAddress(address: "aabbccddeeff")
        #expect(handled)
        #expect(stored?.address == "10.0.0.2")
    }

    @Test func tryUpdateAddressDoesNotContactDevice() async throws {
        try await insertDevice(address: "10.0.0.1")

        _ = await makeService().tryUpdateAddress(macAddress: "aabbccddeeff", address: host)

        #expect(StubURLProtocol.requests(for: host).isEmpty)
    }
}
