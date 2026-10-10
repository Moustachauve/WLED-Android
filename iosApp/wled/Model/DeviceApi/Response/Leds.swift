import Foundation

struct Leds: Decodable {
    var count: Int64?
    var estimatedPowerUsed: Int64?
    var fps: Int64?
    var maxPower: Int64?
    var maxSegment: Int64?
    
    enum CodingKeys: String, CodingKey {
        case count
        case estimatedPowerUsed = "pwr"
        case fps
        case maxPower = "maxpwr"
        case maxSegment = "maxseg"
    }
}
