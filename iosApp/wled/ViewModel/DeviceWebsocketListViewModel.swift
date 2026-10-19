import Foundation
import Shared
import SwiftUI
import Observation
import OSLog

@MainActor
@Observable
final class DeviceWebsocketListViewModel {

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceWebsocketListViewModel"
    )

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
            userDefaults.set(showHiddenDevices, forKey: "DeviceListView.showHiddenDevices")
            updateFilteredDevices(currentTime: Date())
        }
    }

    var showOfflineDevices: Bool = true {
        didSet {
            userDefaults.set(showOfflineDevices, forKey: "DeviceListView.showOfflineDevices")
        }
    }

    // MARK: - Private Properties

    private let connectionManager: DeviceConnectionManager
    private let database: AppDatabase
    private let deviceFirstContactService: DeviceFirstContactService
    private let userDefaults: UserDefaults
    @ObservationIgnored private var discoveryService: DiscoveryService?

    @ObservationIgnored private var deviceMap: [String: DeviceWithState] = [:]

    /// Amount of time after a device becomes offline before it is considered offline.
    private let offlineGracePeriod: TimeInterval = 60

    // MARK: - Initialization

    init(
        connectionManager: DeviceConnectionManager? = nil,
        database: AppDatabase = .shared,
        userDefaults: UserDefaults = .standard
    ) {
        self.database = database
        self.connectionManager = connectionManager ?? database.deviceConnectionManager
        self.deviceFirstContactService = DeviceFirstContactService(repository: database.deviceRepository)
        self.userDefaults = userDefaults

        self.discoveryService = DiscoveryService(
            onDeviceDiscovered: { [weak self] address, macAddress in
                self?.deviceDiscovered(at: address, withMACAddress: macAddress)
            },
            onLocalNetworkPermissionChanged: { [weak self] isGranted in
                self?.localNetworkDenied = !isGranted
            }
        )

        // Load preferences
        self.showHiddenDevices = userDefaults.bool(forKey: "DeviceListView.showHiddenDevices")
        if userDefaults.object(forKey: "DeviceListView.showOfflineDevices") == nil {
            self.showOfflineDevices = true
        } else {
            self.showOfflineDevices = userDefaults.bool(forKey: "DeviceListView.showOfflineDevices")
        }
    }

    // MARK: - Setup and loading

    /// Observes device states, connection pause status, and offline grace-period timers
    /// for as long as the caller's structured task remains active.
    func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeDevices() }
            group.addTask { await self.observePausedState() }
            group.addTask { await self.runOfflineGracePeriodTimer() }
        }
    }

    private func observeDevices() async {
        await database.waitUntilReady()
        for await devices in connectionManager.allDevicesWithState {
            guard !Task.isCancelled else { break }
            syncDevicesWithState(devices)
        }
    }

    private func observePausedState() async {
        for await paused in connectionManager.isPaused {
            guard !Task.isCancelled else { break }
            isPaused = paused.boolValue
        }
    }

    private func runOfflineGracePeriodTimer() async {
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(30))
            guard !Task.isCancelled else { break }
            updateFilteredDevices(currentTime: Date())
        }
    }

    private func syncDevicesWithState(_ sharedDevices: [Shared.DeviceWithState]) {
        var updatedList: [DeviceWithState] = []
        let newMacs = Set(sharedDevices.map { $0.device.macAddress })

        // Prune removed devices from in-memory cache
        deviceMap = deviceMap.filter { newMacs.contains($0.key) }

        for shared in sharedDevices {
            let mac = shared.device.macAddress
            let wrapper: DeviceWithState
            if let existing = deviceMap[mac] {
                if existing.device != shared.device {
                    existing.device = shared.device
                }
                if existing.stateInfo != shared.stateInfo {
                    existing.stateInfo = shared.stateInfo
                }
                if existing.websocketStatus != shared.websocketStatus {
                    existing.websocketStatus = shared.websocketStatus
                }
                wrapper = existing
            } else {
                let newDevice = DeviceWithState(initialDevice: shared.device)
                newDevice.stateInfo = shared.stateInfo
                newDevice.websocketStatus = shared.websocketStatus
                deviceMap[mac] = newDevice
                wrapper = newDevice
            }

            updatedList.append(wrapper)
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
        let manager = connectionManager
        Task {
            _ = try? await manager.setBrightness(macAddress: mac, brightness: Int32(brightness))
        }
    }

    func setDevicePower(for deviceWrapper: DeviceWithState, isOn: Bool) {
        let mac = deviceWrapper.device.macAddress
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
                Self.logger.error("deviceDiscovered: Failed to upsert device: \(error.localizedDescription)")
            }
        }
    }
}
