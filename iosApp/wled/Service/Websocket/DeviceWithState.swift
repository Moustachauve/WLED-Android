import Foundation
import SwiftUI
import Combine
import CoreData

// TODO: This probably shouldn't be in the Websocket folder?

let AP_MODE_MAC_ADDRESS = "00:00:00:00:00:00"

enum WebsocketStatus {
    case connected
    case connecting
    case disconnected
    
    func toString() -> String {
        switch self {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .disconnected: return "Disconnected"
        }
    }
}

@MainActor
class DeviceWithState: ObservableObject, Identifiable {
    private var cancellables = Set<AnyCancellable>()

    @Published var device: Device
    @Published var stateInfo: DeviceStateInfo?
    @Published var websocketStatus: WebsocketStatus = .disconnected
    @Published var availableUpdateVersion: String?

    nonisolated let id: String

    init(initialDevice: Device) {
        self.device = initialDevice
        self.id = initialDevice.macAddress ?? initialDevice.objectID.uriRepresentation().absoluteString

        setupUpdatePipeline()
        setDeviceWillChange()
    }

    private func setDeviceWillChange() {
        // Forward changes from the inner Core Data Device to this wrapper
        // Throttled to prevent high-frequency metadata changes (e.g. lastSeen)
        // from spamming objectWillChange. Critical state like stateInfo and
        // websocketStatus are @Published directly and bypass this throttle.
        device.objectWillChange
            .throttle(for: .milliseconds(500), scheduler: DispatchQueue.main, latest: true)
            .sink { [weak self] _ in
                self?.objectWillChange.send()
            }
            .store(in: &cancellables)
    }

    // MARK: - Calculated properties

    var isOnline: Bool {
        return websocketStatus == .connected
    }
    
    var isAPMode: Bool {
        return device.macAddress == AP_MODE_MAC_ADDRESS
    }

    var hasUpdateAvailable: Bool {
        return !(availableUpdateVersion ?? "").isEmpty
    }

    // MARK: - Update pipeline code

    private func setupUpdatePipeline() {
        $device
            .map { device in
                // This defines which values in the device can cause a
                // recalculation of the currently available version.
                return Publishers.CombineLatest(
                    device.publisher(for: \.branch),
                    device.publisher(for: \.skipUpdateTag)
                )
                .map { (branch: $0, skipTag: $1, device: device) }
            }
            .switchToLatest()
            .combineLatest(
                $stateInfo
                    .debounce(for: .seconds(2), scheduler: DispatchQueue.main)
            )
            .removeDuplicates { prev, curr in
                // Only re-query Core Data if the firmware version actually changed
                prev.1?.info.version == curr.1?.info.version
            }
            .receive(on: DispatchQueue.main) // Perform logic on Main Thread (safe for Core Data)
            .map { (deviceInputs, stateInfo) -> String? in
                let (branchRaw, skipTag, device) = deviceInputs

                // Extract necessary info, fail fast if missing
                guard let info = stateInfo?.info,
                      let currentVersion = info.version,
                      let context = device.managedObjectContext else {
                    return nil
                }

                // Use your existing Service logic
                // Note: We use the raw strings from Core Data to create the Enum
                let branchEnum = Branch(rawValue: branchRaw ?? "") ?? .unknown

                let releaseService = ReleaseService(context: context)
                let newerTag = releaseService.getNewerReleaseTag(
                    versionName: currentVersion,
                    branch: branchEnum,
                    ignoreVersion: skipTag ?? ""
                )

                return newerTag.isEmpty ? nil : newerTag
            }
            .removeDuplicates()
            .assign(to: &$availableUpdateVersion)
    }

    // MARK: - Helper Functions

    /**
     * Get a DeviceWithState that can be used to represent a temporary WLED device in AP mode.
     * Note: Since Device is a Core Data entity, we need a context to create it.
     */
    static func getApModeDeviceWithState(context: NSManagedObjectContext) -> DeviceWithState {
        // Create a new Device entity
        // We assume this is transient and might not be saved to the persistent store immediately
        let device = Device(context: context)
        device.macAddress = AP_MODE_MAC_ADDRESS
        device.address = "4.3.2.1"

        let deviceWithState = DeviceWithState(initialDevice: device)
        deviceWithState.websocketStatus = .connected

        return deviceWithState
    }

    // MARK: Color handling

    var currentColor: Color {
        let colorInt = device.getColor(state: stateInfo?.state)
        let activeColor = colorFromHex(rgbValue: Int(colorInt))

        if isOnline {
            return activeColor
        }

        // Convert to UIColor to easily extract HSB values
        let uiColor = UIColor(activeColor)
        var h: CGFloat = 0, s: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        uiColor.getHue(&h, saturation: &s, brightness: &b, alpha: &a)

        // Return a new Color with 0 saturation (Gray), preserving brightness
        return Color(hue: h, saturation: 0, brightness: b, opacity: Double(a))
    }

    private func colorFromHex(rgbValue: Int, alpha: Double? = 1.0) -> Color {
        // &  binary AND operator to zero out other color values
        // >>  bitwise right shift operator
        // Divide by 0xFF because UIColor takes CGFloats between 0.0 and 1.0

        let red =   CGFloat((rgbValue & 0xFF0000) >> 16) / 0xFF
        let green = CGFloat((rgbValue & 0x00FF00) >> 8) / 0xFF
        let blue =  CGFloat(rgbValue & 0x0000FF) / 0xFF
        let alpha = CGFloat(alpha ?? 1.0)

        return Color(UIColor(red: red, green: green, blue: blue, alpha: alpha))
    }
}

// MARK: - Hashable & Equatable Conformance
extension DeviceWithState: Hashable {
    // Two instances are equal if they are the exact same object in memory
    nonisolated static func == (lhs: DeviceWithState, rhs: DeviceWithState) -> Bool {
        return lhs.id == rhs.id
    }
    
    // Hash based on the object's unique memory address
    nonisolated func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
