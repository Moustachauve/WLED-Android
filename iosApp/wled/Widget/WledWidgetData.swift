import Foundation
import Shared
import UIKit

/// Platform-ready state model for rendering a WLED Home Screen widget on iOS,
/// mirroring Android's `WidgetStateData` and `:shared`'s `WidgetSnapshot`.
struct WledWidgetData: Codable, Equatable, Sendable {
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
        self.init(
            macAddress: snapshot.macAddress,
            address: snapshot.address,
            name: snapshot.name,
            isOn: snapshot.isOn,
            isOnline: snapshot.isOnline,
            color: snapshot.color,
            lastUpdated: snapshot.lastUpdated
        )
    }

    /// Returns a copy with updated device metadata (`name` and `address`).
    func updating(name: String, address: String) -> WledWidgetData {
        WledWidgetData(
            macAddress: macAddress,
            address: address,
            name: name,
            isOn: isOn,
            isOnline: isOnline,
            color: color,
            lastUpdated: lastUpdated
        )
    }

    /// Returns `true` when all visible widget properties (excluding `lastUpdated`) match `other`.
    func hasSameVisibleState(as other: WledWidgetData) -> Bool {
        name == other.name &&
            address == other.address &&
            isOn == other.isOn &&
            isOnline == other.isOnline &&
            color == other.color
    }

    var deviceEntity: WledDeviceEntity {
        WledDeviceEntity(id: macAddress, name: name, address: address)
    }

    var lastUpdatedDate: Date {
        Date(timeIntervalSince1970: TimeInterval(lastUpdated) / 1000.0)
    }

    var deepLinkURL: URL? {
        guard !macAddress.isEmpty else { return nil }
        var components = URLComponents()
        components.scheme = "wled"
        components.host = "device"
        components.path = "/\(macAddress)"
        return components.url
    }

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

    var hueAndSaturation: (hue: Double, saturation: Double) {
        guard color != -1,
              let hsba = UIColor(rgb: Int(color)).hsbaComponents else {
            return (0, 0)
        }
        return (Double(hsba.h), Double(hsba.s))
    }
}
