import Foundation
import Shared

extension Device {
    var displayName: String {
        if !customName.isEmpty {
            return customName
        }
        if !originalName.isEmpty {
            return originalName
        }
        return String(localized: "(New Device)")
    }

    /// Swift-friendly initializer mirroring the Kotlin default arguments, which aren't exported to Swift.
    convenience init(
        macAddress: String,
        address: String,
        isHidden: Bool = false,
        originalName: String = "",
        customName: String = "",
        skipUpdateTag: String = "",
        branch: Branch = .unknown,
        lastSeen: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.init(
            macAddress: macAddress,
            address: address,
            isHidden: isHidden,
            originalName: originalName,
            customName: customName,
            skipUpdateTag: skipUpdateTag,
            branch: branch,
            lastSeen: lastSeen,
            repositoryId: Repository.companion.DEFAULT_ID
        )
    }

    func copy(
        macAddress: String? = nil,
        address: String? = nil,
        isHidden: Bool? = nil,
        originalName: String? = nil,
        customName: String? = nil,
        skipUpdateTag: String? = nil,
        branch: Branch? = nil,
        lastSeen: Int64? = nil,
        repositoryId: Int64? = nil
    ) -> Device {
        Device(
            macAddress: macAddress ?? self.macAddress,
            address: address ?? self.address,
            isHidden: isHidden ?? self.isHidden,
            originalName: originalName ?? self.originalName,
            customName: customName ?? self.customName,
            skipUpdateTag: skipUpdateTag ?? self.skipUpdateTag,
            branch: branch ?? self.branch,
            lastSeen: lastSeen ?? self.lastSeen,
            repositoryId: repositoryId ?? self.repositoryId
        )
    }

    func getColor(state: State?) -> Int64 {
        if let argb = getColorFromState(state: state) {
            return Int64(argb.int32Value) & 0xFFFFFF
        }
        return 0x808080
    }
}
