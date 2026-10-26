import Foundation
import Shared
import Testing
@testable import WLED

struct WledDeviceWidgetTimelineProviderTests {

    let host = "wled-\(UUID().uuidString.lowercased()).test"
    let mac = "mac-\(UUID().uuidString.lowercased())"
    let now = Date(timeIntervalSince1970: 1_700_000_000)
    let database = AppDatabase(inMemory: true, deviceApiFactory: .stubbed)
    let snapshotStore = WledWidgetSnapshotStore(
        userDefaults: UserDefaults(suiteName: "timeline-test-\(UUID().uuidString)"),
        onReloadTimelines: {}
    )

    var controller: WledWidgetController {
        WledWidgetController(database: database, snapshotStore: snapshotStore)
    }

    var provider: WledDeviceWidgetTimelineProvider {
        WledDeviceWidgetTimelineProvider(
            database: database,
            controller: controller,
            snapshotStore: snapshotStore,
            nowProvider: { [now] in now }
        )
    }

    private func insertDevice(
        mac: String? = nil,
        customName: String = "Office Lamp",
        isHidden: Bool = false
    ) async throws -> Device {
        await database.waitUntilReady()
        let device = Device(
            macAddress: mac ?? self.mac,
            address: host,
            isHidden: isHidden,
            originalName: "WLED",
            customName: customName
        )
        try await database.deviceRepository.insert(device: device)
        return device
    }

    private func stubOnlineState(isOn: Bool = true) {
        let body = """
        {
          "on": \(isOn),
          "bri": 200,
          "seg": [{"col": [[0, 191, 255]]}]
        }
        """
        StubURLProtocol.register(.http(statusCode: 200, body: body), for: host)
    }

    @Test func resolveWidgetDataReturnsNilWhenUnconfigured() async throws {
        _ = try await insertDevice(customName: "Alpha Lamp")

        let data = await provider.resolveWidgetData(
            for: SelectWledDeviceWidgetIntent(device: nil),
            preferCachedOnly: false,
            now: now
        )

        #expect(data == nil)
    }

    @Test func resolveWidgetDataReturnsNilWhenConfiguredDeviceWasDeleted() async {
        let deletedEntity = WledDeviceEntity(id: mac, name: "Deleted", address: host)

        let data = await provider.resolveWidgetData(
            for: SelectWledDeviceWidgetIntent(device: deletedEntity),
            preferCachedOnly: false,
            now: now
        )

        #expect(data == nil)
    }

    @Test func resolveWidgetDataReturnsFreshCachedSnapshotWithoutNetworkCall() async throws {
        let device = try await insertDevice(customName: "Renamed Lamp", isHidden: true)
        let freshTimestampMs = Int64(now.timeIntervalSince1970 * 1000) - 1_000
        snapshotStore.save(
            WledWidgetData(
                macAddress: mac,
                address: host,
                name: "Old Name",
                isOn: true,
                isOnline: true,
                color: Int32(bitPattern: 0xFF00BFFF),
                lastUpdated: freshTimestampMs
            ),
            reloadTimelines: false
        )

        let data = await provider.resolveWidgetData(
            for: SelectWledDeviceWidgetIntent(device: WledDeviceEntity(device: device)),
            preferCachedOnly: false,
            now: now
        )

        #expect(StubURLProtocol.requests(for: host).isEmpty)
        #expect(data?.name == "Renamed Lamp")
        #expect(data?.isOn == true)
    }

    @Test func resolveWidgetDataRefreshesOverHttpWhenCachedSnapshotIsStale() async throws {
        let device = try await insertDevice(customName: "Office Lamp")
        let staleTimestampMs = Int64(now.timeIntervalSince1970 * 1000) - 60_000
        snapshotStore.save(
            WledWidgetData(
                macAddress: mac,
                address: host,
                name: "Office Lamp",
                isOn: false,
                isOnline: true,
                color: -1,
                lastUpdated: staleTimestampMs
            ),
            reloadTimelines: false
        )
        stubOnlineState(isOn: true)

        let data = await provider.resolveWidgetData(
            for: SelectWledDeviceWidgetIntent(device: WledDeviceEntity(device: device)),
            preferCachedOnly: false,
            now: now
        )

        #expect(StubURLProtocol.requests(for: host).count == 1)
        #expect(data?.isOn == true)
    }
}
