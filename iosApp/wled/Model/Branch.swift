import Foundation

enum Branch: String, CaseIterable, Identifiable {
    case unknown = ""
    case stable = "stable"
    case beta = "beta"

    var id: Self { self }

    var nameKey: String {
        switch self {
        case .beta: return "Beta"
        case .stable: return "Stable"
        default: return "Unknown"
        }
    }
}

extension Device {
    var branchValue: Branch {
        get {
            guard let branch = self.branch else { return .unknown }
            return Branch(rawValue: String(branch)) ?? .unknown
        }
        set {
            self.branch = String(newValue.rawValue)
        }
    }
}
