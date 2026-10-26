import Foundation
import Shared
import UIKit

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
    let lastUpdated: Int64

    init(
        macAddress: String,
        address: String,
        name: String,
        isOn: Bool,
        isOnline: Bool = true,
        color: Int32 = -1,
        lastUpdated: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.macAddress = macAddress
        self.address = address
        self.name = name
        self.isOn = isOn
        self.isOnline = isOnline
        self.color = color
        self.lastUpdated = lastUpdated
    }

    init(snapshot: WidgetSnapshot) {
        self.macAddress = snapshot.macAddress
        self.address = snapshot.address
        self.name = snapshot.name
        self.isOn = snapshot.isOn
        self.isOnline = snapshot.isOnline
        self.color = snapshot.color
        self.lastUpdated = snapshot.lastUpdated
    }

    /// `WledDeviceEntity` representation used to wire interactive WidgetKit buttons to App Intents.
    var deviceEntity: WledDeviceEntity {
        WledDeviceEntity(id: macAddress, name: name, address: address)
    }

    /// `Date` representation of `lastUpdated` (stored in epoch milliseconds).
    var lastUpdatedDate: Date {
        Date(timeIntervalSince1970: TimeInterval(lastUpdated) / 1000.0)
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

    /// Computes Hue and Saturation (`0.0...1.0`) from the 32-bit ARGB `color` integer.
    var hueAndSaturation: (hue: Double, saturation: Double) {
        guard color != -1,
              let hsba = UIColor(rgb: Int(color)).hsbaComponents else {
            return (0, 0)
        }
        return (Double(hsba.h), Double(hsba.s))
    }
}
