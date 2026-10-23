import Testing
import Foundation
import Shared
@testable import WLED

struct UserDefaultsToDataStoreMigratorTests {

    let database: AppDatabase
    let repository: UserPreferencesRepository
    let testUserDefaults: UserDefaults

    init() throws {
        self.database = AppDatabase(inMemory: true)
        self.repository = database.userPreferencesRepository

        let suiteName = "UserDefaultsToDataStoreMigratorTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: suiteName))
        defaults.removePersistentDomain(forName: suiteName)
        self.testUserDefaults = defaults
    }

    @Test func migratesLegacyPreferenceKeysAndCleansUpUserDefaults() async throws {
        let lastRefreshSeconds: Double = 1_700_000_000
        testUserDefaults.set(true, forKey: UserDefaultsToDataStoreMigrator.legacyShowHiddenDevicesKey)
        testUserDefaults.set(false, forKey: UserDefaultsToDataStoreMigrator.legacyShowOfflineDevicesKey)
        testUserDefaults.set(lastRefreshSeconds, forKey: UserDefaultsToDataStoreMigrator.legacyLastUpdateReleasesDateKey)

        try await UserDefaultsToDataStoreMigrator.migrateIfNeeded(
            repository: repository,
            userDefaults: testUserDefaults
        )

        #expect(testUserDefaults.bool(forKey: UserDefaultsToDataStoreMigrator.migrationKey) == true)
        #expect(testUserDefaults.object(forKey: UserDefaultsToDataStoreMigrator.legacyShowHiddenDevicesKey) == nil)
        #expect(testUserDefaults.object(forKey: UserDefaultsToDataStoreMigrator.legacyShowOfflineDevicesKey) == nil)
        #expect(testUserDefaults.object(forKey: UserDefaultsToDataStoreMigrator.legacyLastUpdateReleasesDateKey) == nil)

        let showHidden = await repository.showHiddenDevices.firstValue()?.boolValue
        let showOfflineLast = await repository.showOfflineDevicesLast.firstValue()?.boolValue
        let lastUpdateCheck = await repository.lastUpdateCheckDate.firstValue()?.int64Value
        #expect(showHidden == true)
        #expect(showOfflineLast == false)
        let expectedNextCheckMs = Int64(lastRefreshSeconds * 1000.0) + 86_400_000
        #expect(lastUpdateCheck == expectedNextCheckMs)
    }

    @Test func skipsMigrationWhenAlreadyCompleted() async throws {
        testUserDefaults.set(true, forKey: UserDefaultsToDataStoreMigrator.migrationKey)
        testUserDefaults.set(true, forKey: UserDefaultsToDataStoreMigrator.legacyShowHiddenDevicesKey)

        try await UserDefaultsToDataStoreMigrator.migrateIfNeeded(
            repository: repository,
            userDefaults: testUserDefaults
        )

        let showHidden = await repository.showHiddenDevices.firstValue()?.boolValue
        #expect(showHidden == false)
    }
}
