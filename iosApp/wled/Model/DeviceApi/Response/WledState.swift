import Foundation

struct WledState: Codable {
    var isOn: Bool?
    var brightness: Int64?
    var transition: Int64?
    var selectedPresetId: Int64?
    var selectedPlaylistId: Int64?
    var nightlight: Nightlight?
    var liveDataOverride: Int64?
    var mainSegment: Int64?
    var segment: [Segment]?
    
    enum CodingKeys: String, CodingKey {
        case isOn = "on"
        case brightness = "bri"
        case transition
        case selectedPresetId = "ps"
        case selectedPlaylistId = "pl"
        case nightlight = "nl"
        case liveDataOverride = "lor"
        case mainSegment = "mainseg"
        case segment = "seg"
    }
}
