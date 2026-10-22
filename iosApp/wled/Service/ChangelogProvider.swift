import Foundation
import OSLog
import Shared

protocol ChangelogProviding: Sendable {
    func getChangelog(lastSeenVersion: String, currentVersion: String) -> [VersionChangelog]
}

struct BundleChangelogProvider: ChangelogProviding {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "BundleChangelogProvider"
    )

    private let bundle: Bundle
    private let isDebugBuild: Bool

    #if DEBUG
    private static let defaultIsDebugBuild = true
    #else
    private static let defaultIsDebugBuild = false
    #endif

    init(bundle: Bundle = .main, isDebugBuild: Bool = Self.defaultIsDebugBuild) {
        self.bundle = bundle
        self.isDebugBuild = isDebugBuild
    }

    func getChangelog(lastSeenVersion: String, currentVersion: String) -> [VersionChangelog] {
        let urlsByFilename = locateChangelogURLs()

        return ChangelogLogic.shared.getChangelog(
            lastSeenVersionStr: lastSeenVersion,
            currentVersionStr: currentVersion,
            listFilenames: { Array(urlsByFilename.keys) },
            isDebugBuild: isDebugBuild,
            readContent: { filename in
                guard let url = urlsByFilename[filename] else { return nil }
                do {
                    return try String(contentsOf: url, encoding: .utf8)
                } catch {
                    Self.logger.error("Failed to read changelog file \(filename): \(error.localizedDescription)")
                    return nil
                }
            }
        )
    }

    private func locateChangelogURLs() -> [String: URL] {
        // `wled/` is a PBXFileSystemSynchronizedRootGroup, so `.md` files in `wled/changelog/`
        // are copied flat to the bundle root.
        let urls = bundle.urls(forResourcesWithExtension: "md", subdirectory: nil) ?? []
        var urlsByFilename: [String: URL] = [:]
        for url in urls {
            urlsByFilename[url.lastPathComponent] = url
        }
        return urlsByFilename
    }
}
