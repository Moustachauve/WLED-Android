import Foundation
import Shared
import SwiftUI

/// Represents a quick action button item in a WLED widget.
struct WledWidgetQuickAction: Identifiable, Equatable, Sendable {
    let id: String
    let label: String

    init(id: String = UUID().uuidString, label: String) {
        self.id = id
        self.label = label
    }
}

/// Platform-ready state model for rendering a WLED Home Screen widget on iOS,
/// mirroring Android's `WidgetStateData` and `:shared`'s `WidgetSnapshot`.
struct WledWidgetData: Equatable, Sendable {
    let macAddress: String
    let address: String
    let name: String
    let isOn: Bool
    let isOnline: Bool
    /// ARGB 32-bit integer (`-1` indicates unknown/default white).
    let color: Int32
    let brightness: Int?
    let lastUpdated: Int64
    let quickActions: [WledWidgetQuickAction]

    init(
        macAddress: String,
        address: String,
        name: String,
        isOn: Bool,
        isOnline: Bool = true,
        color: Int32 = -1,
        brightness: Int? = nil,
        lastUpdated: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        quickActions: [WledWidgetQuickAction] = []
    ) {
        self.macAddress = macAddress
        self.address = address
        self.name = name
        self.isOn = isOn
        self.isOnline = isOnline
        self.color = color
        self.brightness = brightness
        self.lastUpdated = lastUpdated
        self.quickActions = quickActions
    }

    init(snapshot: WidgetSnapshot, quickActions: [WledWidgetQuickAction] = []) {
        self.macAddress = snapshot.macAddress
        self.address = snapshot.address
        self.name = snapshot.name
        self.isOn = snapshot.isOn
        self.isOnline = snapshot.isOnline
        self.color = snapshot.color
        self.brightness = snapshot.brightness?.intValue
        self.lastUpdated = snapshot.lastUpdated
        self.quickActions = quickActions
    }

    /// Deep link URL (`wled://device/<macAddress>`) that opens this device in the main app.
    var deepLinkURL: URL? {
        guard !macAddress.isEmpty else { return nil }
        var components = URLComponents()
        components.scheme = "wled"
        components.host = "device"
        components.path = "/\(macAddress)"
        return components.url
    }

    /// SwiftUI `Color` extracted from the ARGB `color` field, or white when unknown (`-1`).
    var seedColor: Color {
        guard color != -1 else { return .white }
        let rgb = Int(UInt32(bitPattern: color))
        let red = Double((rgb >> 16) & 0xFF) / 255.0
        let green = Double((rgb >> 8) & 0xFF) / 255.0
        let blue = Double(rgb & 0xFF) / 255.0
        return Color(red: red, green: green, blue: blue)
    }
}
