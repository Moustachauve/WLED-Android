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

    @Test func checkChangelogDoesNotPresentWhenLastSeenEqualsCurrentVersion() async throws {
        try await database.userPreferencesRepository.updateLastChangelogVersionSeen(version: "7.0.1")
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
    }

    @Test func checkChangelogSavesCurrentVersionWhenProviderReturnsEmpty() async throws {
        try await database.userPreferencesRepository.updateLastChangelogVersionSeen(version: "7.0.0")
        let provider = StubChangelogProvider(result: [])
        let viewModel = ChangelogViewModel(
            database: database,
            changelogProvider: provider,
            currentVersion: "7.0.1"
        )

        await viewModel.checkChangelog()

        #expect(viewModel.changelogs.isEmpty)
        #expect(provider.recordedCalls.count == 1)
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

    @Test(
        arguments: [
            ("en", "English 8.0.0 notes"),
            ("fr-CA", "Notes 8.0.0 en français"),
            ("de-DE", "Deutsche 8.0.0 Hinweise"),
            ("zh-Hans-CN", "8.0.0 简体中文更新日志")
        ]
    )
    func bundleChangelogProviderSelectsLocalizedFileWhenLanguageMatches(
        languageCode: String,
        expectedContent: String
    ) {
        let fixtures: [String: String] = [
            "8.0.0.md": "English 8.0.0 notes",
            "8.0.0.fr.md": "Notes 8.0.0 en français",
            "8.0.0.de.md": "Deutsche 8.0.0 Hinweise",
            "8.0.0.zh.md": "8.0.0 简体中文更新日志"
        ]
        let baseURL = URL(fileURLWithPath: "/virtual-bundle", isDirectory: true)
        let provider = BundleChangelogProvider(
            isDebugBuild: false,
            languageCodeProvider: { languageCode },
            urlsProvider: { fixtures.keys.map { baseURL.appendingPathComponent($0) } },
            contentReader: { url in
                guard let content = fixtures[url.lastPathComponent] else {
                    throw CocoaError(.fileNoSuchFile)
                }
                return content
            }
        )

        let entries = provider.getChangelog(lastSeenVersion: "7.0.0", currentVersion: "8.0.0")

        #expect(entries == [VersionChangelog(version: "8.0.0", content: expectedContent, isDev: false)])
    }

    @Test func bundleChangelogProviderSkipsUnreadableFile() {
        let baseURL = URL(fileURLWithPath: "/virtual-bundle", isDirectory: true)
        let provider = BundleChangelogProvider(
            isDebugBuild: false,
            languageCodeProvider: { "en" },
            urlsProvider: { [baseURL.appendingPathComponent("8.0.0.md")] },
            contentReader: { _ in throw CocoaError(.fileReadCorruptFile) }
        )

        let entries = provider.getChangelog(lastSeenVersion: "7.0.0", currentVersion: "8.0.0")

        #expect(entries.isEmpty)
    }
}
