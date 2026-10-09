import Foundation

struct DeviceStateInfo: Decodable {
    var state: WledState
    var info: Info
}
