import Foundation

struct Release: Decodable {
    
    var url: String
    var assetsUrl: String
    var uploadUrl: String
    var htmlUrl: String
    var id: Int
    var author: Author
    var nodeId: String
    var tagName: String
    var targetCommitish: String
    var name: String
    var draft: Bool
    var prerelease: Bool
    var createdAt: String
    var publishedAt: String
    var assets: [GithubAsset]
    var tarballUrl: String
    var zipballUrl: String
    var body: String
    var reactions: Reactions?
    var mentionsCount: Int?
    
    enum CodingKeys: String, CodingKey {
        case url
        case assetsUrl = "assets_url"
        case uploadUrl = "upload_url"
        case htmlUrl = "html_url"
        case id
        case author
        case nodeId = "node_id"
        case tagName = "tag_name"
        case targetCommitish = "target_commitish"
        case name
        case draft
        case prerelease
        case createdAt = "created_at"
        case publishedAt = "published_at"
        case assets
        case tarballUrl = "tarball_url"
        case zipballUrl = "zipball_url"
        case body
        case reactions
        case mentionsCount = "mentions_count"
    }
}
