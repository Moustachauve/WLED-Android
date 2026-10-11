import Testing
import Foundation
import Combine
import Shared
@testable import WLED

private typealias WebsocketClient = WLED.WebsocketClient
private typealias WebsocketStatus = WLED.WebsocketStatus

@MainActor
struct DeviceWebsocketListViewModelTests {

    let database: AppDatabase
    let repository: DeviceRepository

    init() {
        self.database = AppDatabase(inMemory: true)
        self.repository = database.deviceRepository
    }

    @Test func testInitialLoadingAndSorting() async throws {
        // 1. Setup mock data
        let device1 = createDevice(name: "Z Device", mac: "01", isHidden: false)
        let device2 = createDevice(name: "A Device", mac: "02", isHidden: false)
        let device3 = createDevice(name: "Hidden Device", mac: "03", isHidden: true)
        try await repository.insert(device: device1)
        try await repository.insert(device: device2)
        try await repository.insert(device: device3)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        viewModel.makeClient = { device in MockWebsocketClient(device: device) }

        // 2. Load — listens to allDevices Flow
        try await load(viewModel, expectedCount: 3)

        #expect(viewModel.allDevicesWithState.count == 3)

        // Hidden devices should be excluded by default
        viewModel.showHiddenDevices = false
        viewModel.updateFilteredDevices(currentTime: Date())

        let allNames = (viewModel.onlineDevices + viewModel.offlineDevices).map { $0.device.displayName }
        #expect(allNames.contains("A Device"))
        #expect(allNames.contains("Z Device"))
        #expect(!allNames.contains("Hidden Device"))
    }

    @Test func testShowingHiddenDevicesUpdatesListImmediately() async throws {
        try await repository.insert(device: createDevice(name: "Hidden Device", mac: "08", isHidden: true))
        let viewModel = DeviceWebsocketListViewModel(database: database)
        viewModel.makeClient = { device in MockWebsocketClient(device: device) }
        try await load(viewModel, expectedCount: 1)
        viewModel.showHiddenDevices = false

        viewModel.showHiddenDevices = true

        let allNames = (viewModel.onlineDevices + viewModel.offlineDevices).map { $0.device.displayName }
        #expect(allNames == ["Hidden Device"])
    }

    @Test func testReactivityToStatusChange() async throws {
        let device = createDevice(name: "Test Device", mac: "01", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        try await load(viewModel, expectedCount: 1)

        // Initially disconnected
        mockClient.setStatus(.disconnected)
        viewModel.updateFilteredDevices(currentTime: Date())

        #expect(viewModel.offlineDevices.count == 1)
        #expect(viewModel.onlineDevices.isEmpty)

        // Switch to connected — the reactive pipeline triggers after debounce
        mockClient.setStatus(.connected)
        try await Task.sleep(for: .seconds(2)) // Wait for 2s debounce + Combine propagation (generous for CI)

        #expect(viewModel.onlineDevices.count == 1)
        #expect(viewModel.offlineDevices.isEmpty)
    }

    @Test func testDeviceUpdatePersistsLastSeen() async throws {
        let device = createDevice(name: "Test Device", mac: "06", isHidden: false)
        try await repository.insert(device: device)
        let viewModel = DeviceWebsocketListViewModel(database: database)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }
        try await load(viewModel, expectedCount: 1)

        mockClient.onDeviceStateUpdated?(.mock(name: "Test Device", version: "0.14.0", color: [255, 0, 0]))

        try await waitUntil { try await repository.findDeviceByMacAddress(address: "06")?.lastSeen ?? 0 > 0 }
        let saved = try await repository.findDeviceByMacAddress(address: "06")
        #expect(saved?.lastSeen ?? 0 > 0)
    }

    @Test func testDatabaseEmissionKeepsNewerInMemoryLastSeen() async throws {
        // Recently persisted lastSeen, so the next websocket update only updates it in memory
        let tenSecondsAgo = Int64(Date().addingTimeInterval(-10).timeIntervalSince1970 * 1000)
        let device = Device(
            macAddress: "07",
            address: "192.168.1.7",
            originalName: "Test Device",
            branch: .stable,
            lastSeen: tenSecondsAgo
        )
        try await repository.insert(device: device)
        let viewModel = DeviceWebsocketListViewModel(database: database)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }
        try await load(viewModel, expectedCount: 1)
        mockClient.onDeviceStateUpdated?(.mock(name: "Test Device", version: "0.14.0", color: [255, 0, 0]))
        let inMemoryLastSeen = mockClient.deviceState.device.lastSeen

        // Unrelated write triggers a database emission carrying the older lastSeen
        try await repository.updateCustomName(macAddress: "07", customName: "Renamed")

        try await waitUntil { mockClient.deviceState.device.customName == "Renamed" }
        #expect(mockClient.deviceState.device.lastSeen == inMemoryLastSeen)
    }

    // MARK: - Helpers

    private func createDevice(name: String, mac: String, isHidden: Bool) -> Device {
        Device(
            macAddress: mac,
            address: "192.168.1.\(mac)",
            isHidden: isHidden,
            originalName: name,
            lastSeen: 0
        )
    }

    /// Starts observing the database and waits until the expected number of devices is loaded.
    private func load(_ viewModel: DeviceWebsocketListViewModel, expectedCount: Int) async throws {
        viewModel.load()
        try await waitUntil { viewModel.allDevicesWithState.count == expectedCount }
    }

    /// Polls `condition` until it's true or the timeout elapses (resilient against CI scheduling delays).
    private func waitUntil(
        timeout: Duration = .seconds(3),
        _ condition: () async throws -> Bool
    ) async throws {
        let clock = ContinuousClock()
        let deadline = clock.now + timeout
        while clock.now < deadline {
            if try await condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
    }

    @Test func testQuickResumeDoesNotDisconnect() async throws {
        let device = createDevice(name: "Test Device Quick Resume", mac: "04", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        try await load(viewModel, expectedCount: 1)
        mockClient.setStatus(.connected)
        viewModel.updateFilteredDevices(currentTime: Date())
        #expect(viewModel.onlineDevices.count == 1)

        // Simulate quick background + resume (app switcher peek)
        viewModel.onPause()
        try await Task.sleep(for: .milliseconds(500)) // Well under the 2s delay
        viewModel.onResume()

        // Device should still be connected — disconnect was cancelled
        try await Task.sleep(for: .seconds(1))
        #expect(mockClient.deviceState.websocketStatus == .connected)
        #expect(viewModel.onlineDevices.count == 1)
    }

    @Test func testFullBackgroundDisconnectsAfterDelay() async throws {
        let device = createDevice(name: "Test Device Background Disconnect", mac: "05", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        viewModel.backgroundDisconnectDelay = .milliseconds(100)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        try await load(viewModel, expectedCount: 1)
        mockClient.setStatus(.connected)
        viewModel.updateFilteredDevices(currentTime: Date())
        #expect(viewModel.onlineDevices.count == 1)

        // Simulate going to background and staying there
        viewModel.onPause()

        // Poll up to 3s for disconnect to complete (resilient against CI CPU scheduling delays)
        for _ in 0..<30 {
            if mockClient.deviceState.websocketStatus == .disconnected { break }
            try await Task.sleep(for: .milliseconds(100))
        }

        // Device should now be disconnected
        #expect(mockClient.deviceState.websocketStatus == .disconnected)
    }
}

// Precise control mock
class ManualMockWebsocketClient: WLED.WebsocketClient {
    func setStatus(_ status: WLED.WebsocketStatus) {
        self.deviceState.websocketStatus = status
    }

    override func connect() { /* no-op */ }
    override func disconnect() {
        self.deviceState.websocketStatus = .disconnected
    }
}
