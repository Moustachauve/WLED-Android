import Foundation
import SwiftUI
import Combine
import CoreData
import Shared

// TODO: This probably shouldn't be in the Websocket folder?

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

    /// Core Data context holding the release cache used to compute `availableUpdateVersion`.
    private let releaseContext: NSManagedObjectContext

    init(
        initialDevice: Device,
        releaseContext: NSManagedObjectContext = PersistenceController.shared.container.viewContext
    ) {
        self.device = initialDevice
        self.id = initialDevice.macAddress
        self.releaseContext = releaseContext

        setupUpdatePipeline()
    }

    // MARK: - Calculated properties

    var isOnline: Bool {
        return websocketStatus == .connected
    }

    var isAPMode: Bool {
        return device.macAddress == DeviceKt.AP_MODE_MAC_ADDRESS
    }

    var hasUpdateAvailable: Bool {
        return !(availableUpdateVersion ?? "").isEmpty
    }

    // MARK: - Update pipeline code

    private func setupUpdatePipeline() {
        $device
            .combineLatest(
                $stateInfo
                    .debounce(for: .seconds(2), scheduler: DispatchQueue.main)
            )
            .removeDuplicates { prev, curr in
                // Only re-query Core Data if the firmware version, branch, or skip tag actually changed
                prev.1?.info.version == curr.1?.info.version &&
                prev.0.branch == curr.0.branch &&
                prev.0.skipUpdateTag == curr.0.skipUpdateTag
            }
            .receive(on: DispatchQueue.main) // Perform logic on Main Thread (safe for Core Data)
            .map { [releaseContext] (device, stateInfo) -> String? in
                guard let info = stateInfo?.info,
                      let currentVersion = info.version else {
                    return nil
                }

                let releaseService = ReleaseService(context: releaseContext)
                let newerTag = releaseService.getNewerReleaseTag(
                    versionName: currentVersion,
                    branch: device.branch,
                    ignoreVersion: device.skipUpdateTag
                )

                return newerTag.isEmpty ? nil : newerTag
            }
            .removeDuplicates()
            .assign(to: &$availableUpdateVersion)
    }

    // MARK: - Helper Functions

    /**
     * Get a DeviceWithState that can be used to represent a temporary WLED device in AP mode.
     */
    static func getApModeDeviceWithState() -> DeviceWithState {
        let device = Device(
            macAddress: DeviceKt.AP_MODE_MAC_ADDRESS,
            address: DeviceKt.DEFAULT_WLED_AP_IP
        )
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
    // Two instances are equal if they represent the same device (same MAC address)
    nonisolated static func == (lhs: DeviceWithState, rhs: DeviceWithState) -> Bool {
        return lhs.id == rhs.id
    }

    // Hash based on the device's MAC address
    nonisolated func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
