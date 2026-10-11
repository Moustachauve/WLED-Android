import Foundation
import Shared
import Combine
import SwiftUI

@MainActor
class DeviceWebsocketListViewModel: NSObject, ObservableObject {

    // MARK: - Published Properties

    // The list of devices with their live state, exposed to the UI
    @Published var allDevicesWithState: [DeviceWithState] = []
    @Published var onlineDevices: [DeviceWithState] = []
    @Published var offlineDevices: [DeviceWithState] = []

    /// Whether the local network permission has been denied by the user.
    @Published var localNetworkDenied: Bool = false

    // Preferences
    @Published var showHiddenDevices: Bool = false {
        didSet {
            UserDefaults.standard.set(showHiddenDevices, forKey: "DeviceListView.showHiddenDevices")
            // Refilter here rather than from `$showHiddenDevices`: @Published emits in willSet,
            // so a subscriber would still read the old value.
            updateFilteredDevices(currentTime: Date())
        }
    }
    @Published var showOfflineDevices: Bool = true {
        didSet {
            UserDefaults.standard.set(showOfflineDevices, forKey: "DeviceListView.showOfflineDevices")
        }
    }

    var makeClient: (Device) -> WebsocketClient = { device in
        WebsocketClient(device: device)
    }

    // MARK: - Private Properties

    private var discoveryService: DiscoveryService?
    private let database: AppDatabase
    private let deviceFirstContactService: DeviceFirstContactService
    private let deviceRepository: DeviceRepository
    private var observeDevicesTask: Task<Void, Never>?

    /// Last `lastSeen` value written to (or read from) the database, per MAC address.
    /// `lastSeen` changes on every websocket message, so it's kept in memory and only persisted periodically.
    private var persistedLastSeen: [String: Int64] = [:]
    private let lastSeenPersistInterval: Int64 = 30_000

    // Map of MacAddress -> Client Wrapper
    // We store the last known address to detect IP changes
    private struct ClientWrapper {
        let client: WebsocketClient
        let lastKnownAddress: String
    }

    private var activeClients: [String: ClientWrapper] = [:]
    private var isPaused = false
    private var backgroundTask: Task<Void, Never>?

    /// Delay before disconnecting websockets after entering background.
    /// Exposed as `internal` so tests can override with a shorter value.
    var backgroundDisconnectDelay: Duration = .seconds(2)

    /// Amount of time after a device becomes offline before it is considered offline.
    private let offlineGracePeriod: TimeInterval = 60
    private var cancellables = Set<AnyCancellable>()

    // MARK: - Initialization

    init(
        database: AppDatabase = .shared,
        clientFactory: ((Device) -> WebsocketClient)? = nil
    ) {
        self.database = database
        self.deviceRepository = database.deviceRepository
        self.deviceFirstContactService = DeviceFirstContactService(repository: database.deviceRepository)
        if let clientFactory = clientFactory {
            self.makeClient = clientFactory
        }
        super.init()

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

        // Periodically refresh for time-based online/offline status changes (grace period)
        Timer.publish(every: 30, on: .main, in: .common)
            .autoconnect()
            .sink { [weak self] time in
                self?.updateFilteredDevices(currentTime: time)
            }
            .store(in: &cancellables)

        // Reactively update when any device status changes or the list itself changes
        $allDevicesWithState
            .map { devices in
                Publishers.MergeMany(devices.map { $0.objectWillChange })
                    .map { _ in () }
                    .prepend(()) // Trigger immediately when the list changes
            }
            .switchToLatest()
            .debounce(for: .milliseconds(200), scheduler: DispatchQueue.main)
            .sink { [weak self] _ in
                self?.updateFilteredDevices(currentTime: Date())
            }
            .store(in: &cancellables)

        // Observe local network permission status from the discovery service
        discoveryService?.$isLocalNetworkGranted
            .compactMap { $0 }
            .receive(on: DispatchQueue.main)
            .sink { [weak self] isGranted in
                self?.localNetworkDenied = !isGranted
            }
            .store(in: &cancellables)
    }

    // MARK: - Setup and loading

    /// Call this when the view appears to initialize data and connections
    func load() {
        // Prevent double loading if already set up
        guard observeDevicesTask == nil else { return }

        let database = database
        let task = Task { [weak self] in
            await database.waitUntilReady()
            for await devices in database.deviceRepository.allDevices {
                // Only hold self for the duration of one emission so the view model can be deallocated
                guard let self, !Task.isCancelled else { break }
                self.updateClients(with: devices)
            }
        }
        observeDevicesTask = task
        // Cancels the observation when the view model is deallocated
        AnyCancellable { task.cancel() }.store(in: &cancellables)
    }

    // MARK: - Client Management Logic

    private func updateClients(with devices: [Device]) {
        let newDeviceMap = Dictionary(uniqueKeysWithValues: devices.map { ($0.macAddress, $0) })

        // 1. Identify and destroy clients for devices that are no longer present
        let currentMacs = Set(activeClients.keys)
        let newMacs = Set(newDeviceMap.keys)
        let macsToRemove = currentMacs.subtracting(newMacs)

        for mac in macsToRemove {
            print("[ListVM] Device removed: \(mac). Destroying client.")
            activeClients[mac]?.client.destroy()
            activeClients[mac] = nil
            persistedLastSeen[mac] = nil
        }

        // 2. Identify and create/update clients for new or changed devices
        for (mac, device) in newDeviceMap {
            let address = device.address
            persistedLastSeen[mac] = max(persistedLastSeen[mac] ?? 0, device.lastSeen)

            if let existingWrapper = activeClients[mac] {
                if existingWrapper.lastKnownAddress != address {
                    // Address changed: Reconnect
                    print("[ListVM] Address changed for \(mac). Recreating client.")
                    existingWrapper.client.destroy()
                    createAndAddClient(for: device, mac: mac)
                } else {
                    // Just a regular update (e.g. name changed). The database copy can lag behind the
                    // in-memory lastSeen, which is only persisted periodically, so keep the newest one.
                    let current = existingWrapper.client.deviceState.device
                    let merged = device.lastSeen >= current.lastSeen ? device : device.copy(lastSeen: current.lastSeen)
                    if current != merged {
                        existingWrapper.client.deviceState.device = merged
                    }
                }
            } else {
                // New Device
                print("[ListVM] Device added: \(mac). Creating client.")
                createAndAddClient(for: device, mac: mac)
            }
        }

        publishState()
    }

    private func createAndAddClient(for device: Device, mac: String) {
        let newClient = makeClient(device)

        newClient.onDeviceStateUpdated = { [weak self] info in
            self?.handleDeviceUpdate(macAddress: mac, info: info)
        }

        if !isPaused {
            newClient.connect()
        }

        activeClients[mac] = ClientWrapper(
            client: newClient,
            lastKnownAddress: device.address
        )
    }

    private func publishState() {
        // Map the clients to the DeviceWithState list expected by the UI
        self.allDevicesWithState = self.activeClients.values.map { wrapper in
            wrapper.client.deviceState
        }
        self.updateFilteredDevices(currentTime: Date())
    }

    // MARK: - Lifecycle (Call these from App ScenePhase)

    func onPause() {
        print("[ListVM] onPause: Scheduling disconnect.")
        flushLastSeen()
        backgroundTask?.cancel()
        let delay = backgroundDisconnectDelay
        backgroundTask = Task { @MainActor [weak self] in
            do {
                try await Task.sleep(for: delay)
            } catch {
                // Task was cancelled (user came back quickly)
                return
            }
            guard let self = self else { return }
            print("[ListVM] onPause: Disconnecting all connections.")
            self.isPaused = true
            self.activeClients.values.forEach { $0.client.disconnect() }
        }
    }

    func onResume() {
        print("[ListVM] onResume: Cancelling pending disconnect and resuming.")
        backgroundTask?.cancel()
        backgroundTask = nil
        guard isPaused else { return } // Nothing to resume if we never disconnected
        isPaused = false
        activeClients.values.forEach { $0.client.connect() }
    }

    // MARK: - Actions

    func refreshOfflineDevices() {
        print("[ListVM] Refreshing offline devices.")
        let offlineClients = activeClients.values.filter { !$0.client.deviceState.isOnline }
        offlineClients.forEach { $0.client.connect() }
    }

    func setBrightness(for deviceWrapper: DeviceWithState, brightness: Int) {
        let mac = deviceWrapper.device.macAddress
        guard let wrapper = activeClients[mac] else {
            print("[ListVM] No active client for \(mac)")
            return
        }
        deviceWrapper.stateInfo?.state.brightness = Int64(brightness)
        wrapper.client.sendState(WledState(brightness: Int64(brightness)))
    }

    func setDevicePower(for deviceWrapper: DeviceWithState, isOn: Bool) {
        let mac = deviceWrapper.device.macAddress
        guard let wrapper = activeClients[mac] else {
            print("[ListVM] No active client for \(mac)")
            return
        }
        deviceWrapper.stateInfo?.state.isOn = isOn
        wrapper.client.sendState(WledState(isOn: isOn))
    }

    func deleteDevice(_ device: Device) {
        print("[ListVM] Deleting device \(device.originalName)")
        Task { [weak self] in
            try? await self?.deviceRepository.delete(device: device)
        }
    }

    func updateFilteredDevices(currentTime: Date) {
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
        print("[ListVM] Starting discovery scan")
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

// MARK: - Device persistence

extension DeviceWebsocketListViewModel {
    private func handleDeviceUpdate(macAddress: String, info: DeviceStateInfo) {
        guard let wrapper = activeClients[macAddress] else { return }
        let deviceState = wrapper.client.deviceState
        let currentDevice = deviceState.device

        let newName = info.info.name
        let newVersion = info.info.version ?? ""
        let now = Int64(Date().timeIntervalSince1970 * 1000)

        var detectedBranch: Branch?
        if currentDevice.branch == .unknown {
            detectedBranch = newVersion.contains("-b") ? .beta : .stable
        }
        let nameChanged = currentDevice.originalName != newName

        // Update in memory right away so the UI and the offline grace period reflect it
        deviceState.device = currentDevice.copy(
            originalName: newName,
            branch: detectedBranch ?? currentDevice.branch,
            lastSeen: now
        )

        // Only perform disk I/O if important data changed or the persisted lastSeen is getting stale
        let structuralChange = nameChanged || detectedBranch != nil
        let lastSeenIsStale = now - (persistedLastSeen[macAddress] ?? 0) >= lastSeenPersistInterval
        persist(
            macAddress: macAddress,
            originalName: nameChanged ? newName : nil,
            branch: detectedBranch,
            lastSeen: structuralChange || lastSeenIsStale ? now : nil
        )
    }

    /// Writes only the provided fields, so concurrent writers (e.g. the edit screen) can't be overwritten
    /// with a stale copy of the device.
    private func persist(
        macAddress: String,
        originalName: String? = nil,
        branch: Branch? = nil,
        lastSeen: Int64? = nil
    ) {
        guard originalName != nil || branch != nil || lastSeen != nil else { return }
        if let lastSeen {
            persistedLastSeen[macAddress] = lastSeen
        }
        let repository = deviceRepository
        Task {
            do {
                if let originalName {
                    try await repository.updateOriginalName(macAddress: macAddress, originalName: originalName)
                }
                if let branch {
                    try await repository.updateBranch(macAddress: macAddress, branch: branch)
                }
                if let lastSeen {
                    try await repository.updateLastSeen(macAddress: macAddress, lastSeen: lastSeen)
                }
            } catch {
                print("[ListVM] Failed to persist device \(macAddress): \(error)")
            }
        }
    }

    /// Persists any in-memory lastSeen that is newer than what's in the database.
    private func flushLastSeen() {
        for (mac, wrapper) in activeClients {
            let lastSeen = wrapper.client.deviceState.device.lastSeen
            if lastSeen > (persistedLastSeen[mac] ?? 0) {
                persist(macAddress: mac, lastSeen: lastSeen)
            }
        }
    }
}
