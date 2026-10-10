import Foundation

struct FileSystem: Decodable {
    var spaceUsed: Int64?
    var spaceTotal: Int64?
    var presetLastModification: Int64?
    
    enum CodingKeys: String, CodingKey {
        case spaceUsed = "u"
        case spaceTotal = "t"
        case presetLastModification = "pmt"
    }
}
