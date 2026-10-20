import Testing
import Foundation
import Shared
@testable import WLED

/// Verifies `DeviceFirstContactService` interop across the Swift/Kotlin boundary:
/// Darwin `URLSession` (`StubURLProtocol`), Room persistence, `@Throws` bridging of
/// `DeviceFirstContactException`, and Swift `Task` cancellation propagation.
/// Core business logic is covered in `:shared` (`DeviceFirstContactServiceTest.kt`).
struct DeviceFirstContactServiceTests {

    private static let apiFactory = DeviceApiFactory.companion.create { configuration in
        configuration.protocolClasses = [StubURLProtocol.self]
    }

    /// Unique per test so stubs never collide when tests run in parallel.
    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let database = AppDatabase(inMemory: true, deviceApiFactory: Self.apiFactory)
    var repository: DeviceRepository { database.deviceRepository }
    var service: DeviceFirstContactService { database.deviceFirstContactService }

    private func stubInfo(name: String = "WLED Office", mac: String = "aabbccddeeff") {
        let body = #"{ "mac": "\#(mac)", "name": "\#(name)", "ver": "0.15.0", "leds": {}, "wifi": {}, "extra": 1 }"#
        StubURLProtocol.register(.http(statusCode: 200, body: body), for: host)
    }

    /// Runs `fetchAndUpsertDevice` against the stubbed host and returns the `DeviceFirstContactException` it threw.
    private func fetchException() async -> DeviceFirstContactException? {
        do {
            _ = try await service.fetchAndUpsertDevice(rawAddress: host)
            Issue.record("Expected fetchAndUpsertDevice to throw DeviceFirstContactException")
            return nil
        } catch {
            return (error as NSError).kotlinException as? DeviceFirstContactException
        }
    }

    @Test func fetchAndUpsertCreatesNewDeviceOverHttpWithRepositoryId() async throws {
        await database.waitUntilReady()
        stubInfo(name: "Kitchen", mac: "001122334455")

        let device = try await service.fetchAndUpsertDevice(rawAddress: host)

        #expect(StubURLProtocol.requests(for: host).map(\.url?.absoluteString) == ["http://\(host)/json/info"])
        let stored = try await repository.findDeviceByMacAddress(address: "001122334455")
        #expect(stored?.address == host)
        #expect(stored?.originalName == "Kitchen")
        #expect(stored?.repositoryId != nil)
        #expect(device.macAddress == "001122334455")
    }

    @Test func fetchAndUpsertBridgesHttpErrorToSwift() async {
        StubURLProtocol.register(.http(statusCode: 500, body: "Oops", contentType: "text/plain"), for: host)

        let error = await fetchException() as? DeviceFirstContactException.HttpError
        #expect(error?.statusCode == 500)
    }

    @Test func fetchAndUpsertBridgesRequestFailedToSwift() async {
        StubURLProtocol.register(.failure(.cannotConnectToHost), for: host)

        #expect(await fetchException() is DeviceFirstContactException.RequestFailed)
    }

    @Test func fetchAndUpsertPropagatesSwiftTaskCancellation() async {
        StubURLProtocol.register(.never, for: host)
        let service = service
        let host = host

        let task = Task { try await service.fetchAndUpsertDevice(rawAddress: host) }
        task.cancel()

        await #expect(throws: CancellationError.self) { try await task.value }
    }
}
