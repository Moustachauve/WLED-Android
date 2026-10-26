import Foundation
import Shared
import SwiftUI

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

    init(
        macAddress: String,
        address: String,
        name: String,
        isOn: Bool,
        isOnline: Bool = true,
        color: Int32 = -1,
        brightness: Int? = nil,
        lastUpdated: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.macAddress = macAddress
        self.address = address
        self.name = name
        self.isOn = isOn
        self.isOnline = isOnline
        self.color = color
        self.brightness = brightness
        self.lastUpdated = lastUpdated
    }

    init(snapshot: WidgetSnapshot) {
        self.macAddress = snapshot.macAddress
        self.address = snapshot.address
        self.name = snapshot.name
        self.isOn = snapshot.isOn
        self.isOnline = snapshot.isOnline
        self.color = snapshot.color
        self.brightness = snapshot.brightness?.intValue
        self.lastUpdated = snapshot.lastUpdated
    }

    /// `WledDeviceEntity` representation used to wire interactive WidgetKit buttons to App Intents.
    var deviceEntity: WledDeviceEntity {
        WledDeviceEntity(id: macAddress, name: name, address: address)
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

    /// Parses a canonical `wled://device/<macAddress>` deep link URL and returns the MAC address.
    static func parseDeepLinkMacAddress(_ url: URL) -> String? {
        guard url.scheme?.caseInsensitiveCompare("wled") == .orderedSame,
              let host = url.host(percentEncoded: false),
              host.caseInsensitiveCompare("device") == .orderedSame else {
            return nil
        }
        let segments = url.pathComponents.filter { $0 != "/" }
        guard segments.count == 1 else { return nil }
        let mac = segments[0].trimmingCharacters(in: .whitespacesAndNewlines)
        return mac.isEmpty ? nil : mac
    }

    /// Computes Hue and Saturation (`0.0...1.0`) directly from the 32-bit ARGB `color` integer.
    var hueAndSaturation: (hue: Double, saturation: Double) {
        guard color != -1 else { return (0, 0) }
        let rgb = Int(UInt32(bitPattern: color))
        let red = Double((rgb >> 16) & 0xFF) / 255.0
        let green = Double((rgb >> 8) & 0xFF) / 255.0
        let blue = Double(rgb & 0xFF) / 255.0

        let maxChannel = max(red, green, blue)
        let minChannel = min(red, green, blue)
        let delta = maxChannel - minChannel

        guard maxChannel > 0, delta > 0 else {
            return (0, 0)
        }

        let saturation = delta / maxChannel
        let rawHue: Double
        if maxChannel == red {
            rawHue = ((green - blue) / delta).truncatingRemainder(dividingBy: 6.0)
        } else if maxChannel == green {
            rawHue = ((blue - red) / delta) + 2.0
        } else {
            rawHue = ((red - green) / delta) + 4.0
        }
        let normalizedHue = (rawHue / 6.0) < 0 ? (rawHue / 6.0) + 1.0 : (rawHue / 6.0)
        return (normalizedHue, saturation)
    }
}
