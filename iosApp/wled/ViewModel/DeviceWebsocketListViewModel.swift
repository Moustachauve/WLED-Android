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
    var visibleDevices: [DeviceWithState] = []
    var onlineDevices: [DeviceWithState] = []
    var offlineDevices: [DeviceWithState] = []

    /// Whether the local network permission has been denied by the user.
    var localNetworkDenied: Bool = false

    /// Whether device connections are currently paused (e.g. backgrounded).
    var isPaused: Bool = false

    // Preferences
    var showHiddenDevices: Bool = false {
        didSet {
            guard showHiddenDevices != oldValue else { return }
            updateFilteredDevices(currentTime: Date())
            guard !isSyncingPreferences else { return }
            let showHidden = showHiddenDevices
            let repository = userPreferencesRepository
            Task {
                try? await repository.updateShowHiddenDevices(showHiddenDevices: showHidden)
            }
        }
    }

    var showOfflineDevicesLast: Bool = true {
        didSet {
            guard showOfflineDevicesLast != oldValue, !isSyncingPreferences else { return }
            let showOfflineLast = showOfflineDevicesLast
            let repository = userPreferencesRepository
            Task {
                try? await repository.updateShowOfflineDeviceLast(showOfflineDeviceLast: showOfflineLast)
            }
        }
    }

    var theme: ThemeSettings = .auto {
        didSet {
            guard theme != oldValue, !isSyncingPreferences else { return }
            let selectedTheme = theme
            let repository = userPreferencesRepository
            Task {
                try? await repository.updateThemeMode(themeSettings: selectedTheme)
            }
        }
    }

    // MARK: - Private Properties

    private let connectionManager: DeviceConnectionManager
    private let database: AppDatabase
    private let deviceFirstContactService: DeviceFirstContactService
    private let userPreferencesRepository: UserPreferencesRepository
    @ObservationIgnored private let discoveryService: any DeviceDiscovering

    @ObservationIgnored private var deviceMap: [String: DeviceWithState] = [:]
    @ObservationIgnored private var latestSharedDevices: [Shared.DeviceWithState] = []
    @ObservationIgnored private var isSyncingPreferences = false

    /// Amount of time (in milliseconds) after a device becomes offline before it is considered offline.
    private static let offlineGracePeriodMs: Int64 = 60_000

    // MARK: - Initialization

    init(
        connectionManager: DeviceConnectionManager? = nil,
        database: AppDatabase = .shared,
        discoveryService: (any DeviceDiscovering)? = nil
    ) {
        self.database = database
        self.connectionManager = connectionManager ?? database.deviceConnectionManager
        self.deviceFirstContactService = database.deviceFirstContactService
        self.userPreferencesRepository = database.userPreferencesRepository

        let resolvedDiscoveryService = discoveryService ?? DiscoveryService()
        self.discoveryService = resolvedDiscoveryService

        resolvedDiscoveryService.onDeviceDiscovered = { [weak self] address, macAddress in
            self?.deviceDiscovered(at: address, withMACAddress: macAddress)
        }
        resolvedDiscoveryService.onLocalNetworkPermissionChanged = { [weak self] isGranted in
            self?.localNetworkDenied = !isGranted
        }
    }

    // MARK: - Setup and loading

    /// Observes device states, user preferences, connection pause status, and offline grace-period timers
    /// for as long as the caller's structured task remains active.
    func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeDevices() }
            group.addTask { await self.observeShowHiddenDevices() }
            group.addTask { await self.observeShowOfflineDevicesLast() }
            group.addTask { await self.observeThemeMode() }
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

    private func observeShowHiddenDevices() async {
        await database.waitUntilReady()
        for await showHidden in userPreferencesRepository.showHiddenDevices {
            guard !Task.isCancelled else { break }
            isSyncingPreferences = true
            showHiddenDevices = showHidden.boolValue
            isSyncingPreferences = false
        }
    }

    private func observeShowOfflineDevicesLast() async {
        await database.waitUntilReady()
        for await showOfflineLast in userPreferencesRepository.showOfflineDevicesLast {
            guard !Task.isCancelled else { break }
            isSyncingPreferences = true
            showOfflineDevicesLast = showOfflineLast.boolValue
            isSyncingPreferences = false
        }
    }

    private func observeThemeMode() async {
        await database.waitUntilReady()
        for await currentTheme in userPreferencesRepository.themeMode {
            guard !Task.isCancelled else { break }
            isSyncingPreferences = true
            theme = currentTheme
            isSyncingPreferences = false
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
        latestSharedDevices = sharedDevices
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
                if existing.availableUpdateVersion != shared.updateVersionTag {
                    existing.availableUpdateVersion = shared.updateVersionTag
                }
                wrapper = existing
            } else {
                let newDevice = DeviceWithState(initialDevice: shared.device)
                newDevice.stateInfo = shared.stateInfo
                newDevice.websocketStatus = shared.websocketStatus
                newDevice.availableUpdateVersion = shared.updateVersionTag
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
        discoveryService.stop()
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
        let currentTimeMillis = Int64(currentTime.timeIntervalSince1970 * 1000.0)
        let partitioned = PartitionedDevicesKt.partitionDevices(
            devices: latestSharedDevices,
            showHiddenDevices: showHiddenDevices,
            currentTimeMillis: currentTimeMillis,
            offlineGracePeriodMs: Self.offlineGracePeriodMs
        )
        let visible = partitioned.visible.compactMap { deviceMap[$0.device.macAddress] }
        let online = partitioned.online.compactMap { deviceMap[$0.device.macAddress] }
        let offline = partitioned.offline.compactMap { deviceMap[$0.device.macAddress] }

        // Only update if content changed to avoid unnecessary SwiftUI view body evaluations
        if self.visibleDevices != visible {
            self.visibleDevices = visible
        }
        if self.onlineDevices != online {
            self.onlineDevices = online
        }
        if self.offlineDevices != offline {
            self.offlineDevices = offline
        }
    }

    // MARK: - Discovery Logic

    func startDiscovery() {
        discoveryService.scan()
    }

    func stopDiscovery() {
        discoveryService.stop()
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
                try await deviceFirstContactService.processDiscoveredDevice(
                    rawAddress: address,
                    macAddress: macAddress
                )
            } catch {
                Self.logger.error("deviceDiscovered: Failed to upsert device: \(error.localizedDescription)")
            }
        }
    }
}
