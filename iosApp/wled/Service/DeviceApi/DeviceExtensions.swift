import Foundation
import Shared

extension Device {
    convenience init(
        macAddress: String,
        address: String,
        isHidden: Bool = false,
        originalName: String = "",
        customName: String = "",
        skipUpdateTag: String = "",
        branch: Branch = .unknown,
        lastSeen: Int64 = 0
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
            repositoryId: 1
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

    func getColor(state: WledState?) -> Int64 {
        guard let state = state,
              let colorInfo = state.segment?.first?.colors?.first,
              colorInfo.count >= 3
        else {
            // Return neutral Gray if any data is missing
            return 0x808080
        }

        let red = Int64(Double(colorInfo[0]) + 0.5)
        let green = Int64(Double(colorInfo[1]) + 0.5)
        let blue = Int64(Double(colorInfo[2]) + 0.5)
        return (red << 16) | (green << 8) | blue
    }
}
