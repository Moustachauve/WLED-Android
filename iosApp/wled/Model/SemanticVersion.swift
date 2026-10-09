import Foundation

/// A structural representation of semantic versions to allow robust comparison.
struct SemanticVersion: Comparable {
    let major: Int
    let minor: Int
    let patch: Int
    let preRelease: String?

    init?(_ versionString: String) {
        var v = versionString.trimmingCharacters(in: .whitespacesAndNewlines)
        if v.lowercased().hasPrefix("v") {
            v = String(v.dropFirst())
        }
        
        let parts = v.split(separator: "-", maxSplits: 1)
        guard !parts.isEmpty else { return nil }
        
        let versionParts = parts[0].split(separator: ".")
        
        guard versionParts.count >= 2,
              let major = Int(versionParts[0]),
              let minor = Int(versionParts[1]) else {
            return nil
        }
        
        self.major = major
        self.minor = minor
        self.patch = versionParts.count > 2 ? (Int(versionParts[2]) ?? 0) : 0
        self.preRelease = parts.count > 1 ? String(parts[1]) : nil
    }

    /// Returns a `SemanticVersion` containing only the major, minor, and patch components.
    var baseVersion: SemanticVersion {
        return SemanticVersion(major: major, minor: minor, patch: patch, preRelease: nil)
    }

    private init(major: Int, minor: Int, patch: Int, preRelease: String?) {
        self.major = major
        self.minor = minor
        self.patch = patch
        self.preRelease = preRelease
    }

    /// Checks if this version is at least the other version, optionally ignoring the pre-release identifier.
    /// - Parameters:
    ///   - other: The version to compare against.
    ///   - ignorePreRelease: If `true`, the comparison ignores pre-release identifiers (e.g., 0.16.0-b1 is treated as 0.16.0).
    /// - Returns: `true` if this version is greater than or equal to the other version.
    func isAtLeast(_ other: SemanticVersion, ignorePreRelease: Bool = false) -> Bool {
        if ignorePreRelease {
            return baseVersion >= other.baseVersion
        }
        return self >= other
    }

    static func < (lhs: SemanticVersion, rhs: SemanticVersion) -> Bool {
        if lhs.major != rhs.major { return lhs.major < rhs.major }
        if lhs.minor != rhs.minor { return lhs.minor < rhs.minor }
        if lhs.patch != rhs.patch { return lhs.patch < rhs.patch }
        
        // Pre-releases are strictly less than stable releases
        if let lhsPre = lhs.preRelease, let rhsPre = rhs.preRelease {
            // Further comparison logic for pre-releases, e.g. b1 < b2
            // Simple string comparison is sufficient for WLED versions
            return lhsPre < rhsPre
        }
        if lhs.preRelease != nil && rhs.preRelease == nil {
            return true  // left is pre-release, right is stable (so left < right)
        }
        if lhs.preRelease == nil && rhs.preRelease != nil {
            return false // left is stable, right is pre-release (so left > right)
        }
        
        return false // Exactly equal
    }
    
    static func == (lhs: SemanticVersion, rhs: SemanticVersion) -> Bool {
        return lhs.major == rhs.major &&
               lhs.minor == rhs.minor &&
               lhs.patch == rhs.patch &&
               lhs.preRelease == rhs.preRelease
    }
}
