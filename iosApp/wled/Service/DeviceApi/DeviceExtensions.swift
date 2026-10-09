import Foundation

extension Device {
    var displayName: String {
        if let name = customName, !name.isEmpty {
            return name
        }
        if let name = originalName, !name.isEmpty {
            return name
        }
        return String(localized: "(New Device)")
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

extension Device: Observable { }
