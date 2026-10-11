import Testing
import Foundation
import Combine
import Shared
@testable import WLED

private typealias WebsocketClient = WLED.WebsocketClient
private typealias WebsocketStatus = WLED.WebsocketStatus

@MainActor
struct DeviceWebsocketListViewModelTests {

    let database: DevicesDatabase
    let repository: DeviceRepository

    init() {
        self.database = DevicesDatabase.companion.createInMemoryDatabase()
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

        let viewModel = DeviceWebsocketListViewModel(deviceRepository: repository)
        viewModel.makeClient = { device in MockWebsocketClient(device: device) }

        // 2. Load — listens to allDevices Flow
        viewModel.load()
        try await Task.sleep(for: .milliseconds(200))

        #expect(viewModel.allDevicesWithState.count == 3)

        // Hidden devices should be excluded by default
        viewModel.showHiddenDevices = false
        viewModel.updateFilteredDevices(currentTime: Date())

        let allNames = (viewModel.onlineDevices + viewModel.offlineDevices).map { $0.device.displayName }
        #expect(allNames.contains("A Device"))
        #expect(allNames.contains("Z Device"))
        #expect(!allNames.contains("Hidden Device"))
    }

    @Test func testReactivityToStatusChange() async throws {
        let device = createDevice(name: "Test Device", mac: "01", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(deviceRepository: repository)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        viewModel.load()
        try await Task.sleep(for: .milliseconds(200))

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

    // MARK: - Helpers

    private func createDevice(name: String, mac: String, isHidden: Bool) -> Device {
        Device(
            macAddress: mac,
            address: "192.168.1.\(mac)",
            isHidden: isHidden,
            originalName: name
        )
    }

    @Test func testQuickResumeDoesNotDisconnect() async throws {
        let device = createDevice(name: "Test Device Quick Resume", mac: "04", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(deviceRepository: repository)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        viewModel.load()
        try await Task.sleep(for: .milliseconds(200))
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

        let viewModel = DeviceWebsocketListViewModel(deviceRepository: repository)
        viewModel.backgroundDisconnectDelay = .milliseconds(100)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        viewModel.load()
        try await Task.sleep(for: .milliseconds(200))
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
