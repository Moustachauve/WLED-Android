import Foundation
import Shared

extension Branch: @retroactive Identifiable {
    public var id: Self { self }

    /// The string stored in the legacy Core Data `Device.branch` attribute.
    var legacyValue: String {
        switch self {
        case .beta: return "beta"
        case .stable: return "stable"
        default: return ""
        }
    }

    /// Parses the string stored in the legacy Core Data `Device.branch` attribute.
    init(legacyValue: String) {
        switch legacyValue.lowercased() {
        case "beta": self = .beta
        case "stable": self = .stable
        default: self = .unknown
        }
    }

    var nameKey: String {
        switch self {
        case .beta: return "Beta"
        case .stable: return "Stable"
        default: return "Unknown"
        }
    }
}
