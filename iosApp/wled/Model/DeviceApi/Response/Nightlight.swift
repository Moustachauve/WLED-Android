import Foundation

struct Nightlight: Codable {
    var isOn: Bool?
    var duration: Int64?
    var fade: Bool?
    var mode: Int64?
    var targetBrightness: Int64?
    var remainingTime: Int64?
    
    enum CodingKeys: String, CodingKey {
        case isOn = "on"
        case duration = "dur"
        case fade
        case mode
        case targetBrightness = "tbri"
        case remainingTime = "rem"
    }
}
