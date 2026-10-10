import Foundation

struct Wifi: Decodable {
    var bssid: String?
    var rssi: Int64?
    var signal: Int64?
    var channel: Int64?
    
    enum CodingKeys: String, CodingKey {
        case bssid
        case rssi
        case signal
        case channel
    }
}
