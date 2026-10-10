import Foundation

struct Reactions: Decodable {

    var url: String
    var totalCount: Int
    var positive: Int
    var negative: Int
    var laugh: Int
    var hooray: Int
    var confused: Int
    var heart: Int
    var rocket: Int
    var eyes: Int
    
    enum CodingKeys: String, CodingKey {
        case url
        case totalCount = "total_count"
        case positive = "+1"
        case negative = "-1"
        case laugh
        case hooray
        case confused
        case heart
        case rocket
        case eyes
    }
}
