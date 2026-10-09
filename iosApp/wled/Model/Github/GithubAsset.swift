import Foundation

struct GithubAsset: Decodable {

    var url: String
    var id: Int64
    var nodeId: String
    var name: String
    var label: String?
    var uploader: Uploader
    var contentType: String
    var state: String
    var size: Int64
    var downloadCount: Int
    var createdAt: String
    var updatedAt: String
    var browserDownloadUrl: String

    enum CodingKeys: String, CodingKey {
        case url
        case id
        case nodeId = "node_id"
        case name 
        case label
        case uploader
        case contentType = "content_type"
        case state
        case size
        case downloadCount = "download_count"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
        case browserDownloadUrl = "browser_download_url"
    }
}
