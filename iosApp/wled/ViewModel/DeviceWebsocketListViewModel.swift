import Foundation
import Shared
import Combine
import SwiftUI
import Observation

@MainActor
@Observable
class DeviceWebsocketListViewModel {

    // MARK: - Observable Properties

    // The list of devices with their live state, exposed to the UI
    var allDevicesWithState: [DeviceWithState] = []
    var onlineDevices: [DeviceWithState] = []
    var offlineDevices: [DeviceWithState] = []

    /// Whether the local network permission has been denied by the user.
    var localNetworkDenied: Bool = false

    /// Whether device connections are currently paused (e.g. backgrounded).
    var isPaused: Bool = false

    // Preferences
    var showHiddenDevices: Bool = false {
        didSet {
            UserDefaults.standard.set(showHiddenDevices, forKey: "DeviceListView.showHiddenDevices")
            updateFilteredDevices(currentTime: Date())
        }
    }

    var showOfflineDevices: Bool = true {
        didSet {
            UserDefaults.standard.set(showOfflineDevices, forKey: "DeviceListView.showOfflineDevices")
        }
    }

    // MARK: - Private Properties

    private let connectionManager: DeviceConnectionManager
    private let database: AppDatabase
    private let deviceFirstContactService: DeviceFirstContactService
    private var discoveryService: DiscoveryService?

    private var deviceMap: [String: DeviceWithState] = [:]

    private nonisolated(unsafe) var observeDevicesTask: Task<Void, Never>?
    private nonisolated(unsafe) var observePausedTask: Task<Void, Never>?
    private nonisolated(unsafe) var refreshTimerTask: Task<Void, Never>?

    /// Amount of time after a device becomes offline before it is considered offline.
    private let offlineGracePeriod: TimeInterval = 60
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialization

    init(
        connectionManager: DeviceConnectionManager? = nil,
        database: AppDatabase = .shared
    ) {
        self.database = database
        self.connectionManager = connectionManager ?? database.deviceConnectionManager
        self.deviceFirstContactService = DeviceFirstContactService(repository: database.deviceRepository)

        self.discoveryService = DiscoveryService { [weak self] address, macAddress in
            Task { @MainActor [weak self] in
                self?.deviceDiscovered(at: address, withMACAddress: macAddress)
            }
        }

        // Load preferences
        self.showHiddenDevices = UserDefaults.standard.bool(forKey: "DeviceListView.showHiddenDevices")
        if UserDefaults.standard.object(forKey: "DeviceListView.showOfflineDevices") == nil {
            self.showOfflineDevices = true
        } else {
            self.showOfflineDevices = UserDefaults.standard.bool(forKey: "DeviceListView.showOfflineDevices")
        }

        // Observe local network permission status from the discovery service
        discoveryService?.$isLocalNetworkGranted
            .compactMap { $0 }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isGranted in
                self?.localNetworkDenied = !isGranted
            }
            .store(in: &cancellables)
    }

    deinit {
        observeDevicesTask?.cancel()
        observePausedTask?.cancel()
        refreshTimerTask?.cancel()
    }

    // MARK: - Setup and loading

    /// Call this when the view appears to initialize data and connections
    func load() {
        guard observeDevicesTask == nil else { return }

        let database = database
        let manager = connectionManager

        observeDevicesTask = Task { [weak self] in
            await database.waitUntilReady()
            for await devices in manager.allDevicesWithState {
                guard let self, !Task.isCancelled else { break }
                self.syncDevicesWithState(devices)
            }
        }

        observePausedTask = Task { [weak self] in
            for await paused in manager.isPaused {
                guard let self, !Task.isCancelled else { break }
                self.isPaused = (paused as? Bool) ?? (paused as? KotlinBoolean)?.boolValue ?? false
            }
        }

        // Periodically refresh for time-based online/offline status changes (grace period)
        refreshTimerTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(30))
                guard let self, !Task.isCancelled else { break }
                self.updateFilteredDevices(currentTime: Date())
            }
        }
    }

    private func syncDevicesWithState(_ sharedDevices: [Shared.DeviceWithState]) {
        var updatedList: [DeviceWithState] = []
        let newMacs = Set(sharedDevices.map { $0.device.macAddress })

        // Prune removed devices from in-memory cache
        deviceMap = deviceMap.filter { newMacs.contains($0.key) }

        for shared in sharedDevices {
            let mac = shared.device.macAddress
            if let existing = deviceMap[mac] {
                existing.device = shared.device
                existing.stateInfo = shared.stateInfo
                existing.websocketStatus = shared.websocketStatus
                if let updateTag = shared.updateVersionTag {
                    existing.availableUpdateVersion = updateTag
                }
                updatedList.append(existing)
            } else {
                let newDevice = DeviceWithState(initialDevice: shared.device)
                newDevice.stateInfo = shared.stateInfo
                newDevice.websocketStatus = shared.websocketStatus
                if let updateTag = shared.updateVersionTag {
                    newDevice.availableUpdateVersion = updateTag
                }
                deviceMap[mac] = newDevice
                updatedList.append(newDevice)
            }
        }

        self.allDevicesWithState = updatedList
        self.updateFilteredDevices(currentTime: Date())
    }

    // MARK: - Lifecycle (Call these from App ScenePhase)

    func onPause() {
        connectionManager.onPause()
    }

    func onResume() {
        connectionManager.onResume()
    }

    // MARK: - Actions

    func refreshOfflineDevices() {
        connectionManager.refreshOfflineDevices()
    }

    func setBrightness(for deviceWrapper: DeviceWithState, brightness: Int) {
        let mac = deviceWrapper.device.macAddress
        if let current = deviceWrapper.stateInfo {
            deviceWrapper.stateInfo = current.withBrightness(brightness: Int32(brightness))
        }
        let manager = connectionManager
        Task {
            _ = try? await manager.setBrightness(macAddress: mac, brightness: Int32(brightness))
        }
    }

    func setDevicePower(for deviceWrapper: DeviceWithState, isOn: Bool) {
        let mac = deviceWrapper.device.macAddress
        if let current = deviceWrapper.stateInfo {
            deviceWrapper.stateInfo = current.withIsOn(isOn: isOn)
        }
        let manager = connectionManager
        Task {
            _ = try? await manager.setPower(macAddress: mac, isOn: isOn)
        }
    }

    func deleteDevice(_ device: Device) {
        let manager = connectionManager
        Task {
            try? await manager.deleteDevice(device: device)
        }
    }

    func updateFilteredDevices(currentTime: Date = Date()) {
        let visible = allDevicesWithState.filter { showHiddenDevices || !$0.device.isHidden }
        let sorted = visible.sorted {
            $0.device.displayName.localizedStandardCompare($1.device.displayName) == .orderedAscending
        }

        var online: [DeviceWithState] = []
        var offline: [DeviceWithState] = []
        for device in sorted {
            let isConsideredOnline = device.isOnline || {
                let lastSeenDate = Date(timeIntervalSince1970: TimeInterval(device.device.lastSeen) / 1000.0)
                return currentTime.timeIntervalSince(lastSeenDate) < offlineGracePeriod
            }()
            if isConsideredOnline {
                online.append(device)
            } else {
                offline.append(device)
            }
        }

        // Only update if content changed to avoid unnecessary SwiftUI view body evaluations
        if self.onlineDevices != online {
            self.onlineDevices = online
        }
        if self.offlineDevices != offline {
            self.offlineDevices = offline
        }
    }

    // MARK: - Discovery Logic

    func startDiscovery() {
        discoveryService?.scan()
    }

    /// Opens the app's Settings page where the user can toggle Local Network permission.
    func openSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private func deviceDiscovered(at address: String, withMACAddress macAddress: String?) {
        Task {
            // Don't upsert discovered devices until legacy devices have been imported
            await database.waitUntilReady()
            do {
                if await !deviceFirstContactService
                    .tryUpdateAddress(macAddress: macAddress, address: address) {
                    _ = try await deviceFirstContactService
                        .fetchAndUpsertDevice(rawAddress: address)
                }
            } catch {
                print("deviceDiscovered: Failed to upsert device: \(error)")
            }
        }
    }
}
