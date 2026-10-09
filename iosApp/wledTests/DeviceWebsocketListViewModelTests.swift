import Testing
import Foundation
import CoreData
import Combine
@testable import WLED

@MainActor
struct DeviceWebsocketListViewModelTests {

    let container: NSPersistentContainer
    let context: NSManagedObjectContext

    init() {
        self.container = PersistenceController(inMemory: true).container
        self.context = container.viewContext
    }

    @Test func testInitialLoadingAndSorting() async throws {
        // 1. Setup mock data
        let device1 = createDevice(name: "Z Device", mac: "01", isHidden: false)
        let device2 = createDevice(name: "A Device", mac: "02", isHidden: false)
        let device3 = createDevice(name: "Hidden Device", mac: "03", isHidden: true)
        try context.save()

        let viewModel = DeviceWebsocketListViewModel(context: context)
        viewModel.makeClient = { device in MockWebsocketClient(device: device) }
        
        // 2. Load — filtering now happens synchronously
        viewModel.load()
        
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
        try context.save()

        let viewModel = DeviceWebsocketListViewModel(context: context)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }
        
        viewModel.load()
        
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
        let device = Device(context: context)
        device.address = "192.168.1.\(mac)" // Required field
        device.macAddress = mac
        device.originalName = name
        device.isHidden = isHidden
        return device
    }

    @Test func testQuickResumeDoesNotDisconnect() async throws {
        let device = createDevice(name: "Test Device", mac: "01", isHidden: false)
        try context.save()

        let viewModel = DeviceWebsocketListViewModel(context: context)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        viewModel.load()
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
        let device = createDevice(name: "Test Device", mac: "01", isHidden: false)
        try context.save()

        let viewModel = DeviceWebsocketListViewModel(context: context)
        viewModel.backgroundDisconnectDelay = .milliseconds(100)
        let mockClient = ManualMockWebsocketClient(device: device)
        viewModel.makeClient = { _ in mockClient }

        viewModel.load()
        mockClient.setStatus(.connected)
        viewModel.updateFilteredDevices(currentTime: Date())
        #expect(viewModel.onlineDevices.count == 1)

        // Simulate going to background and staying there
        viewModel.onPause()
        try await Task.sleep(for: .milliseconds(500)) // Wait well past the 100ms delay

        // Device should now be disconnected
        #expect(mockClient.deviceState.websocketStatus == .disconnected)
    }
}

// Precise control mock
class ManualMockWebsocketClient: WebsocketClient {
    func setStatus(_ status: WebsocketStatus) {
        self.deviceState.websocketStatus = status
    }
    
    override func connect() { /* no-op */ }
    override func disconnect() {
        self.deviceState.websocketStatus = .disconnected
    }
}
