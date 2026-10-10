import Foundation
import CoreData
import Combine
import SwiftUI

@MainActor
class DeviceWebsocketListViewModel: NSObject, ObservableObject, NSFetchedResultsControllerDelegate {
    
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
    private let deviceFirstContactService = DeviceFirstContactService()
    private let context: NSManagedObjectContext
    private var frc: NSFetchedResultsController<Device>!
    
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
    private let sortingQueue = DispatchQueue(label: "com.wled.DeviceSortingQueue")

    // MARK: - Initialization
    
    init(context: NSManagedObjectContext) {
        self.context = context
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

        // Reactively update when preferences change
        Publishers.CombineLatest($showHiddenDevices, $showOfflineDevices)
            .dropFirst()
            .sink { [weak self] _ in
                self?.updateFilteredDevices(currentTime: Date())
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
        guard frc == nil else { return }

        setupFetchedResultsController()

        // Initial population of clients
        try? frc.performFetch()
        if let objects = frc.fetchedObjects {
            updateClients(with: objects)
        }
    }

    // MARK: - Core Data Setup
    
    private func setupFetchedResultsController() {
        let request = NSFetchRequest<Device>(entityName: "Device")
        // Sort by lastSeen or name as a default
        request.sortDescriptors = [NSSortDescriptor(key: "lastSeen", ascending: false)]
        
        frc = NSFetchedResultsController(
            fetchRequest: request,
            managedObjectContext: context,
            sectionNameKeyPath: nil,
            cacheName: nil
        )
        frc.delegate = self
    }
    
    // MARK: - Client Management Logic
    
    private func updateClients(with devices: [Device]) {
        let newDeviceMap = Dictionary(uniqueKeysWithValues: devices.compactMap { device -> (String, Device)? in
            guard let mac = device.macAddress else { return nil }
            return (mac, device)
        })
        
        // 1. Identify and destroy clients for devices that are no longer present
        let currentMacs = Set(activeClients.keys)
        let newMacs = Set(newDeviceMap.keys)
        let macsToRemove = currentMacs.subtracting(newMacs)
        
        for mac in macsToRemove {
            print("[ListVM] Device removed: \(mac). Destroying client.")
            activeClients[mac]?.client.destroy()
            activeClients[mac] = nil
        }
        
        // 2. Identify and create/update clients for new or changed devices
        for (mac, device) in newDeviceMap {
            let address = device.address ?? ""
            
            if let existingWrapper = activeClients[mac] {
                if existingWrapper.lastKnownAddress != address {
                    // Address changed: Reconnect
                    print("[ListVM] Address changed for \(mac). Recreating client.")
                    existingWrapper.client.destroy()
                    createAndAddClient(for: device, mac: mac)
                } else {
                    // Just a regular update (e.g. name changed), the ObservableObject DeviceWithState handles this automatically
                    // because it holds the reference to the Core Data object.
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
            self?.handleDeviceUpdate(deviceID: device.objectID, info: info)
        }

        if !isPaused {
            newClient.connect()
        }
        
        activeClients[mac] = ClientWrapper(
            client: newClient,
            lastKnownAddress: device.address ?? ""
        )
    }

    private func handleDeviceUpdate(deviceID: NSManagedObjectID, info: DeviceStateInfo) {
        context.perform {
            guard let device = try? self.context.existingObject(with: deviceID) as? Device else { return }

            // Logic moved from WebsocketClient to here
            let newName = info.info.name
            let newVersion = info.info.version ?? ""

            // Flag to determine if we need an immediate disk write
            var structuralChange = false

            var currentBranch = device.branchValue
            if currentBranch == Branch.unknown {
                if newVersion.contains("-b") {
                    currentBranch = Branch.beta
                } else {
                    currentBranch = Branch.stable
                }
                device.branchValue = currentBranch
                structuralChange = true
            }
            if device.originalName != newName {
                device.originalName = newName
                structuralChange = true
            }

            // Update transient data (Updates UI in-memory, but no disk save needed yet)
            device.lastSeen = Int64(Date().timeIntervalSince1970 * 1000)

            // Only perform disk I/O if important data changed
            if structuralChange && self.context.hasChanges {
                try? self.context.save()
            }
        }
    }

    private func publishState() {
        // Map the clients to the DeviceWithState list expected by the UI
        self.allDevicesWithState = self.activeClients.values.map { wrapper in
            wrapper.client.deviceState
        }
        self.updateFilteredDevices(currentTime: Date())
    }
    
    // MARK: - NSFetchedResultsControllerDelegate
    
    nonisolated func controllerDidChangeContent(_ controller: NSFetchedResultsController<any NSFetchRequestResult>) {
        Task { @MainActor in
            guard let devices = self.frc.fetchedObjects else { return }
            self.updateClients(with: devices)
        }
    }

    // MARK: - Lifecycle (Call these from App ScenePhase)
    
    func onPause() {
        print("[ListVM] onPause: Scheduling disconnect.")
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
            if self.context.hasChanges {
                try? self.context.save()
            }
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
        guard let mac = deviceWrapper.device.macAddress,
              let wrapper = activeClients[mac] else {
            print("[ListVM] No active client for \(deviceWrapper.device.macAddress ?? "nil")")
            return
        }
        deviceWrapper.stateInfo?.state.brightness = Int64(brightness)
        wrapper.client.sendState(WledState(brightness: Int64(brightness)))
    }
    
    func setDevicePower(for deviceWrapper: DeviceWithState, isOn: Bool) {
        guard let mac = deviceWrapper.device.macAddress,
              let wrapper = activeClients[mac] else {
            print("[ListVM] No active client for \(deviceWrapper.device.macAddress ?? "nil")")
            return
        }
        deviceWrapper.stateInfo?.state.isOn = isOn
        wrapper.client.sendState(WledState(isOn: isOn))
    }
    
    func deleteDevice(_ device: Device) {
        print("[ListVM] Deleting device \(device.originalName ?? "")")
        // Capture context locally to avoid isolation issues in the closure
        let objectID = device.objectID
        let ctx = context
        ctx.perform {
            if let deviceToDelete = try? ctx.existingObject(with: objectID) {
                ctx.delete(deviceToDelete)
                try? ctx.save()
            }
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
