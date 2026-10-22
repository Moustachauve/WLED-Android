import Foundation
import Observation
import OSLog
import Shared

@MainActor
@Observable
final class ChangelogViewModel {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "ChangelogViewModel"
    )

    var changelogs: [VersionChangelog] = []

    private let database: AppDatabase
    private let changelogProvider: any ChangelogProviding
    private let currentVersion: String

    init(
        database: AppDatabase = .shared,
        changelogProvider: any ChangelogProviding = BundleChangelogProvider(),
        currentVersion: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0.0"
    ) {
        self.database = database
        self.changelogProvider = changelogProvider
        self.currentVersion = currentVersion
    }

    func checkChangelog() async {
        await database.waitUntilReady()
        let lastSeenVersion = await readLastSeenVersion()

        if lastSeenVersion.isEmpty {
            await saveLastSeenVersion(currentVersion)
            return
        }

        if lastSeenVersion == currentVersion {
            return
        }

        let entries = changelogProvider.getChangelog(
            lastSeenVersion: lastSeenVersion,
            currentVersion: currentVersion
        )
        if !entries.isEmpty {
            changelogs = entries
        } else {
            await saveLastSeenVersion(currentVersion)
        }
    }

    func dismiss() async {
        changelogs = []
        await saveLastSeenVersion(currentVersion)
    }

    func showAllChangelogs() {
        let entries = changelogProvider.getChangelog(
            lastSeenVersion: "0.0.0",
            currentVersion: currentVersion
        )
        if !entries.isEmpty {
            changelogs = entries
        }
    }

    private func readLastSeenVersion() async -> String {
        for await version in database.userPreferencesRepository.lastChangelogVersionSeen {
            return version
        }
        return ""
    }

    private func saveLastSeenVersion(_ version: String) async {
        do {
            try await database.userPreferencesRepository.updateLastChangelogVersionSeen(version: version)
        } catch {
            Self.logger.error("Failed to save lastChangelogVersionSeen: \(error.localizedDescription)")
        }
    }
}
