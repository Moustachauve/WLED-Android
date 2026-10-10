import Foundation

struct Info: Decodable {
    var leds: Leds
    var wifi: Wifi
    var version: String?
    var buildId: Int64?
    /// Added in 0.15
    var codeName: String?
    /// Added in 0.15
    var release: String?
    var name: String
    var syncToggleReceive: Bool?
    var udpPort: Int64?
    /// Added in 0.15
    var simplifiedUI: Bool?
    var isUpdatedLive: Bool?
    var liveSegment: Int64?
    var realtimeMode: String?
    var realtimeIp: String?
    var websocketClientCount: Int64?
    var effectCount: Int64?
    var paletteCount: Int64?
    var customPaletteCount: Int64?
    // Missing: maps
    var fileSystem: FileSystem?
    var nodeListCount: Int64?
    var platformName: String?
    var arduinoCoreVersion: String?
    /// Added in 0.15
    var clockFrequency: Int64?
    /// Added in 0.15
    var flashChipSize: Int64?
    /// lwip is deprecated and is supposed to be removed in 0.14.0
    var lwip: Int64?
    var freeHeap: Int64?
    var uptime: Int64?
    var time: String?
    /// Contains some extra options status in the form of a bitset
    var opt: Int64?
    var brand: String?
    var product: String?
    var mac: String?
    var ipAddress: String?
    // Missing: u - UserMods
    
    enum CodingKeys: String, CodingKey {
        case leds
        case wifi
        case version = "ver"
        case buildId = "vid"
        case codeName = "cn"
        case release
        case name
        case syncToggleReceive = "str"
        case udpPort = "udpport"
        case simplifiedUI = "simplifiedui"
        case isUpdatedLive = "live"
        case liveSegment = "liveseg"
        case realtimeMode = "lm"
        case realtimeIp = "lip"
        case websocketClientCount = "ws"
        case effectCount = "fxcount"
        case paletteCount = "palcount"
        case customPaletteCount = "cpalcount"
        case fileSystem = "fs"
        case nodeListCount = "ndc"
        case platformName = "arch"
        case arduinoCoreVersion = "core"
        case clockFrequency = "clock"
        case flashChipSize = "flash"
        case lwip
        case freeHeap
        case uptime
        case time
        case opt
        case brand
        case product
        case mac
        case ipAddress = "ip"
    }
}
