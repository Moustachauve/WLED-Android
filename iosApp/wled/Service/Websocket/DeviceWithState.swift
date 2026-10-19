import Foundation
import SwiftUI
import Observation
import CoreData
import Shared

typealias WebsocketStatus = Shared.WebsocketStatus

extension WebsocketStatus {
    func toString() -> String {
        switch self {
        case .connected: return "Connected"
        case .connecting: return "Connecting"
        case .disconnected: return "Disconnected"
        }
    }
}

@MainActor
@Observable
final class DeviceWithState: Identifiable {

    var device: Device {
        didSet {
            if oldValue.branch != device.branch || oldValue.skipUpdateTag != device.skipUpdateTag {
                scheduleUpdateCheck(immediate: true)
            }
        }
    }
    var stateInfo: DeviceStateInfo? {
        didSet {
            if oldValue?.info.version != stateInfo?.info.version {
                scheduleUpdateCheck(immediate: false)
            }
        }
    }
    var websocketStatus: WebsocketStatus = .disconnected
    var availableUpdateVersion: String?

    nonisolated let id: String

    /// Core Data context holding the release cache used to compute `availableUpdateVersion`.
    @ObservationIgnored private let releaseContext: NSManagedObjectContext
    @ObservationIgnored private var updateCheckTask: Task<Void, Never>?

    init(
        initialDevice: Device,
        releaseContext: NSManagedObjectContext = PersistenceController.shared.container.viewContext
    ) {
        self.device = initialDevice
        self.id = initialDevice.macAddress
        self.releaseContext = releaseContext
    }

    isolated deinit {
        updateCheckTask?.cancel()
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

    private func scheduleUpdateCheck(immediate: Bool) {
        updateCheckTask?.cancel()
        updateCheckTask = Task { [weak self] in
            if !immediate {
                try? await Task.sleep(for: .seconds(2))
            }
            guard let self, !Task.isCancelled else { return }
            self.evaluateUpdateAvailability()
        }
    }

    private func evaluateUpdateAvailability() {
        guard let currentVersion = stateInfo?.info.version else {
            if availableUpdateVersion != nil {
                availableUpdateVersion = nil
            }
            return
        }

        let releaseService = ReleaseService(context: releaseContext)
        let newerTag = releaseService.getNewerReleaseTag(
            versionName: currentVersion,
            branch: device.branch,
            ignoreVersion: device.skipUpdateTag
        )
        let newValue = newerTag.isEmpty ? nil : newerTag
        if availableUpdateVersion != newValue {
            availableUpdateVersion = newValue
        }
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
