import Foundation
import Shared

extension Branch: @retroactive Identifiable {
    public var id: Self { self }

    public var rawValue: String {
        switch self {
        case .beta: return "beta"
        case .stable: return "stable"
        default: return ""
        }
    }

    public init?(rawValue: String) {
        switch rawValue.lowercased() {
        case "beta": self = .beta
        case "stable": self = .stable
        default: self = .unknown
        }
    }

    public var nameKey: String {
        switch self {
        case .beta: return "Beta"
        case .stable: return "Stable"
        default: return "Unknown"
        }
    }
}
