import Foundation
import Shared
import Testing
@testable import WLED

private final class StubChangelogProvider: ChangelogProviding, @unchecked Sendable {
    let result: [VersionChangelog]
    private(set) var recordedCalls: [(lastSeenVersion: String, currentVersion: String)] = []

    init(result: [VersionChangelog] = []) {
        self.result = result
    }

    func getChangelog(lastSeenVersion: String, currentVersion: String) -> [VersionChangelog] {
        recordedCalls.append((lastSeenVersion: lastSeenVersion, currentVersion: currentVersion))
        return result
    }
}

@MainActor
struct ChangelogViewModelTests {

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func checkChangelogSavesVersionWithoutShowingOnFirstInstall() async throws {
        let provider = StubChangelogProvider(
            result: [VersionChangelog(version: "7.0.1", content: "New features", isDev: false)]
        )
        let viewModel = ChangelogViewModel(
            database: database,
            changelogProvider: provider,
            currentVersion: "7.0.1"
        )

        await viewModel.checkChangelog()

        #expect(viewModel.changelogs.isEmpty)
        #expect(provider.recordedCalls.isEmpty)
        #expect(await readStoredVersion() == "7.0.1")
    }

    @Test func checkChangelogPresentsContentWhenUpgradingVersion() async throws {
        try await database.userPreferencesRepository.updateLastChangelogVersionSeen(version: "7.0.0")
        let provider = StubChangelogProvider(
            result: [VersionChangelog(version: "7.0.1", content: "### Feature A", isDev: false)]
        )
        let viewModel = ChangelogViewModel(
            database: database,
            changelogProvider: provider,
            currentVersion: "7.0.1"
        )

        await viewModel.checkChangelog()

        #expect(viewModel.changelogs.map(\.version) == ["7.0.1"])
        #expect(provider.recordedCalls.count == 1)
        #expect(provider.recordedCalls.first?.lastSeenVersion == "7.0.0")
        #expect(provider.recordedCalls.first?.currentVersion == "7.0.1")
    }

    @Test func dismissClearsContentAndPersistsCurrentVersion() async throws {
        try await database.userPreferencesRepository.updateLastChangelogVersionSeen(version: "7.0.0")
        let provider = StubChangelogProvider(
            result: [VersionChangelog(version: "7.0.1", content: "### Feature A", isDev: false)]
        )
        let viewModel = ChangelogViewModel(
            database: database,
            changelogProvider: provider,
            currentVersion: "7.0.1"
        )
        await viewModel.checkChangelog()

        await viewModel.dismiss()

        #expect(viewModel.changelogs.isEmpty)
        #expect(await readStoredVersion() == "7.0.1")
    }

    @Test func showAllChangelogsLoadsAllVersionsUpToCurrent() {
        let provider = StubChangelogProvider(
            result: [
                VersionChangelog(version: "7.0.1", content: "Patch 7.0.1", isDev: false),
                VersionChangelog(version: "7.0.0", content: "Initial 7.0", isDev: false)
            ]
        )
        let viewModel = ChangelogViewModel(
            database: database,
            changelogProvider: provider,
            currentVersion: "7.0.1"
        )

        viewModel.showAllChangelogs()

        #expect(viewModel.changelogs.map(\.version) == ["7.0.1", "7.0.0"])
        #expect(provider.recordedCalls.count == 1)
        #expect(provider.recordedCalls.first?.lastSeenVersion == "0.0.0")
        #expect(provider.recordedCalls.first?.currentVersion == "7.0.1")
    }

    private func readStoredVersion() async -> String? {
        for await version in database.userPreferencesRepository.lastChangelogVersionSeen {
            return version
        }
        return nil
    }
}
