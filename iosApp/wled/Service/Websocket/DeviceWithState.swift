import Foundation
import SwiftUI
import Observation
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

    var device: Device
    var stateInfo: DeviceStateInfo?
    var websocketStatus: WebsocketStatus = .disconnected
    var availableUpdateVersion: String?

    nonisolated let id: String

    init(initialDevice: Device) {
        self.device = initialDevice
        self.id = initialDevice.macAddress
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
        let uiColor = UIColor(rgb: Int(device.getColor(state: stateInfo?.state)))

        if isOnline {
            return Color(uiColor)
        }

        let hsba = uiColor.hsbaComponents ?? HSBA(h: 0, s: 0, b: 0, a: 1)

        // Return a new Color with 0 saturation (Gray), preserving brightness
        return Color(hue: hsba.h, saturation: 0, brightness: hsba.b, opacity: Double(hsba.a))
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
