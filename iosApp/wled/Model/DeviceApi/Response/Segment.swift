import Foundation

struct Segment: Codable {
    var id: Int64?
    var start: Int64?
    var stop: Int64?
    var length: Int64?
    var grouping: Int64?
    var spacing: Int64?
    var isOn: Bool?
    var brightness: Int64?
    var colors: [[Int64]]?
    var effect: Int64?
    var effectSpeed: Int64?
    var effectInt64ensity: Int64?
    var palette: Int64?
    var isSelected: Bool?
    var isReversed: Bool?
    var isMirrored: Bool?
    
    enum CodingKeys: String, CodingKey {
        case id
        case start
        case stop
        case length = "len"
        case grouping = "grp"
        case spacing = "spc"
        case isOn = "on"
        case brightness = "bri"
        case colors = "col"
        case effect = "fx"
        case effectSpeed = "sx"
        case effectInt64ensity = "ix"
        case palette = "pal"
        case isSelected = "sel"
        case isReversed = "rev"
        case isMirrored = "mi"
    }
}
