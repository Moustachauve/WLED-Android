import Testing
import Foundation
import Combine
import Shared
@testable import WLED

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
        #expect(allNames == ["A Device", "Z Device"])
    }

    @Test func testShowingHiddenDevicesUpdatesListImmediately() async throws {
        try await repository.insert(device: createDevice(name: "Hidden Device", mac: "08", isHidden: true))
        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)
        viewModel.showHiddenDevices = false

        viewModel.showHiddenDevices = true

        let allNames = (viewModel.onlineDevices + viewModel.offlineDevices).map { $0.device.displayName }
        #expect(allNames == ["Hidden Device"])
    }

    @Test func testDisconnectedStatusPlacesDeviceInOfflineList() async throws {
        let device = createDevice(name: "Test Device", mac: "01", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: device,
                stateInfo: nil,
                websocketStatus: .disconnected,
                updateVersionTag: nil
            )
        )
        try await waitUntil { viewModel.offlineDevices.count == 1 && viewModel.onlineDevices.isEmpty }
        #expect(viewModel.offlineDevices.count == 1)
        #expect(viewModel.onlineDevices.isEmpty)
    }

    @Test func testConnectedStatusMovesDeviceToOnlineList() async throws {
        let device = createDevice(name: "Test Device", mac: "01", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: device,
                stateInfo: nil,
                websocketStatus: .connected,
                updateVersionTag: nil
            )
        )
        try await waitUntil { viewModel.onlineDevices.count == 1 && viewModel.offlineDevices.isEmpty }
        #expect(viewModel.onlineDevices.count == 1)
        #expect(viewModel.offlineDevices.isEmpty)
    }

    @Test func testDeviceUpdateReflectsInViewModel() async throws {
        let device = createDevice(name: "Original Name", mac: "06", isHidden: false)
        try await repository.insert(device: device)
        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        let updatedDevice = device.copy(originalName: "Updated Name", lastSeen: 123456789)
        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: updatedDevice,
                stateInfo: nil,
                websocketStatus: .connected,
                updateVersionTag: nil
            )
        )

        try await waitUntil {
            viewModel.allDevicesWithState.first?.device.originalName == "Updated Name"
        }
        #expect(viewModel.allDevicesWithState.first?.device.originalName == "Updated Name")
        #expect(viewModel.allDevicesWithState.first?.device.lastSeen == 123456789)
    }

    @Test func testClearedUpdateVersionTagClearsAvailableUpdateVersion() async throws {
        let device = createDevice(name: "Updatable Device", mac: "10", isHidden: false)
        try await repository.insert(device: device)
        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: device,
                stateInfo: nil,
                websocketStatus: .connected,
                updateVersionTag: "v0.15.0"
            )
        )
        try await waitUntil {
            viewModel.allDevicesWithState.first?.availableUpdateVersion == "v0.15.0"
        }

        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: device,
                stateInfo: nil,
                websocketStatus: .connected,
                updateVersionTag: nil
            )
        )
        try await waitUntil {
            viewModel.allDevicesWithState.first?.availableUpdateVersion == nil
        }
        #expect(viewModel.allDevicesWithState.first?.availableUpdateVersion == nil)
    }

    @Test func testFailedPowerToggleRollsBackOptimisticState() async throws {
        let device = createDevice(name: "Offline Light", mac: "11", isHidden: false)
        try await repository.insert(device: device)
        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        let initialState = DeviceStateInfo.companion.createMock(
            name: "Offline Light",
            version: "0.14.0",
            red: 255,
            green: 160,
            blue: 0,
            brightness: 128,
            isOn: false
        )
        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: device,
                stateInfo: initialState,
                websocketStatus: .disconnected,
                updateVersionTag: nil
            )
        )
        try await waitUntil {
            viewModel.allDevicesWithState.first?.stateInfo?.state.isOn?.boolValue == false
        }

        let wrapper = try #require(viewModel.allDevicesWithState.first)
        viewModel.setDevicePower(for: wrapper, isOn: true)
        #expect(wrapper.stateInfo?.state.isOn?.boolValue == true)

        try await waitUntil {
            wrapper.stateInfo?.state.isOn?.boolValue == false
        }
        #expect(wrapper.stateInfo?.state.isOn?.boolValue == false)
    }

    @Test func testQuickResumeCancelsPendingPause() async throws {
        let device = createDevice(name: "Test Device Quick Resume", mac: "04", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        // Simulate quick background + resume (app switcher peek)
        viewModel.onPause()
        try await Task.sleep(for: .milliseconds(200)) // Well under the 2s pause delay
        viewModel.onResume()

        try await Task.sleep(for: .milliseconds(500))
        #expect(viewModel.isPaused == false)
    }

    @Test func testFullBackgroundPausesConnections() async throws {
        let testDatabase = makeFastPauseDatabase()
        let device = createDevice(name: "Test Device Background Pause", mac: "05", isHidden: false)
        try await testDatabase.deviceRepository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: testDatabase)
        try await load(viewModel, expectedCount: 1)

        viewModel.onPause()

        try await waitUntil(timeout: .seconds(3)) { viewModel.isPaused }
        #expect(viewModel.isPaused == true)
    }

    @Test func testResumeAfterBackgroundPauseClearsPausedState() async throws {
        let testDatabase = makeFastPauseDatabase()
        let device = createDevice(name: "Test Device Resume", mac: "07", isHidden: false)
        try await testDatabase.deviceRepository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: testDatabase)
        try await load(viewModel, expectedCount: 1)
        viewModel.onPause()
        try await waitUntil(timeout: .seconds(3)) { viewModel.isPaused }

        viewModel.onResume()

        try await waitUntil(timeout: .seconds(2)) { !viewModel.isPaused }
        #expect(viewModel.isPaused == false)
    }

    @Test func testDeleteDeviceRemovesFromDatabase() async throws {
        let device = createDevice(name: "To Be Deleted", mac: "09", isHidden: false)
        try await repository.insert(device: device)

        let viewModel = DeviceWebsocketListViewModel(database: database)
        try await load(viewModel, expectedCount: 1)

        viewModel.deleteDevice(device)

        try await waitUntil {
            try await repository.findDeviceByMacAddress(address: "09") == nil
        }
        let remaining = try await repository.findDeviceByMacAddress(address: "09")
        #expect(remaining == nil)
    }

    // MARK: - Helpers

    private func makeFastPauseDatabase() -> AppDatabase {
        let fastPauseConfig = DeviceConnectionManagerConfig(
            backgroundPauseDelayMs: 50,
            lastSeenPersistIntervalMs: 30000,
            updateCheckCacheTtlMs: 3600000,
            updateCheckRetryIntervalMs: 60000
        )
        return AppDatabase(inMemory: true, config: fastPauseConfig)
    }

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
        timeout: Duration = .seconds(10),
        _ condition: () async throws -> Bool
    ) async throws {
        let clock = ContinuousClock()
        let deadline = clock.now + timeout
        while clock.now < deadline {
            if try await condition() { return }
            try await Task.sleep(for: .milliseconds(20))
        }
        Issue.record("Timed out after \(timeout) waiting for condition")
    }
}
