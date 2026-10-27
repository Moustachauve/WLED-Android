import Foundation
import Shared
import UIKit

extension WifiSignalLevel {
    var symbolName: String {
        self == .offline ? "wifi.slash" : "wifi"
    }

    var variableValue: Double {
        switch self {
        case .good:
            1.0
        case .fair:
            0.64
        case .weak:
            0.33
        case .none, .offline:
            0.0
        }
    }
}

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
    let signalLevel: WifiSignalLevel
    let lastUpdated: Int64

    private enum CodingKeys: String, CodingKey {
        case macAddress
        case address
        case name
        case isOn
        case isOnline
        case color
        case signalLevel
        case lastUpdated
    }

    init(
        macAddress: String,
        address: String,
        name: String,
        isOn: Bool,
        isOnline: Bool = true,
        color: Int32 = -1,
        signalLevel: WifiSignalLevel? = nil,
        lastUpdated: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.macAddress = macAddress
        self.address = address
        self.name = name
        self.isOn = isOn
        self.isOnline = isOnline
        self.color = color
        self.signalLevel = isOnline
            ? (signalLevel.flatMap { $0 == .offline ? nil : $0 } ?? .good)
            : .offline
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
            signalLevel: snapshot.signalLevel,
            lastUpdated: snapshot.lastUpdated
        )
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let isOnline = try container.decode(Bool.self, forKey: .isOnline)
        let rawSignalName = try container.decodeIfPresent(String.self, forKey: .signalLevel)
        let decodedSignal = rawSignalName.flatMap { name in
            WifiSignalLevel.allCases.first { $0.name == name }
        }
        self.init(
            macAddress: try container.decode(String.self, forKey: .macAddress),
            address: try container.decode(String.self, forKey: .address),
            name: try container.decode(String.self, forKey: .name),
            isOn: try container.decode(Bool.self, forKey: .isOn),
            isOnline: isOnline,
            color: try container.decode(Int32.self, forKey: .color),
            signalLevel: decodedSignal,
            lastUpdated: try container.decode(Int64.self, forKey: .lastUpdated)
        )
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(macAddress, forKey: .macAddress)
        try container.encode(address, forKey: .address)
        try container.encode(name, forKey: .name)
        try container.encode(isOn, forKey: .isOn)
        try container.encode(isOnline, forKey: .isOnline)
        try container.encode(color, forKey: .color)
        try container.encode(signalLevel.name, forKey: .signalLevel)
        try container.encode(lastUpdated, forKey: .lastUpdated)
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
            signalLevel: signalLevel,
            lastUpdated: lastUpdated
        )
    }

    /// Returns `true` when all visible widget properties (excluding `lastUpdated`) match `other`.
    func hasSameVisibleState(as other: WledWidgetData) -> Bool {
        name == other.name &&
            address == other.address &&
            isOn == other.isOn &&
            isOnline == other.isOnline &&
            color == other.color &&
            signalLevel == other.signalLevel
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
