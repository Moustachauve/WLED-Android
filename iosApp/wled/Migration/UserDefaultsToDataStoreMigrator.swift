import Foundation
import OSLog
import Shared

struct UserDefaultsToDataStoreMigrator {
    static let migrationKey = "hasCompletedUserDefaultsToDataStoreMigration_v1"
    static let legacyShowHiddenDevicesKey = "DeviceListView.showHiddenDevices"
    static let legacyShowOfflineDevicesKey = "DeviceListView.showOfflineDevices"
    static let legacyLastUpdateReleasesDateKey = "lastUpdateReleasesDate_v2"
    static let preChangelogBaselineVersion = "7.0.1"

    private static let legacyKeys = [
        legacyShowHiddenDevicesKey,
        legacyShowOfflineDevicesKey,
        legacyLastUpdateReleasesDateKey
    ]
    private static let oneDayMilliseconds: Int64 = 24 * 60 * 60 * 1000

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "UserDefaultsToDataStoreMigrator"
    )

    /// Migrates legacy `UserDefaults` preference keys into `UserPreferencesRepository` once,
    /// then records `migrationKey` and cleans up the legacy keys.
    static func migrateIfNeeded(
        repository: UserPreferencesRepository,
        userDefaults: UserDefaults = .standard,
        hasMigratedDevices: Bool = false
    ) async throws {
        guard !userDefaults.bool(forKey: migrationKey) else {
            return
        }

        logger.info("Starting UserDefaults to DataStore preferences migration...")

        if userDefaults.object(forKey: legacyShowHiddenDevicesKey) != nil {
            let showHidden = userDefaults.bool(forKey: legacyShowHiddenDevicesKey)
            try await repository.updateShowHiddenDevices(showHiddenDevices: showHidden)
        }

        if userDefaults.object(forKey: legacyShowOfflineDevicesKey) != nil {
            let showOffline = userDefaults.bool(forKey: legacyShowOfflineDevicesKey)
            try await repository.updateShowOfflineDeviceLast(showOfflineDeviceLast: showOffline)
        }

        let lastRefreshTimestamp = userDefaults.double(forKey: legacyLastUpdateReleasesDateKey)
        if lastRefreshTimestamp > 0 {
            let nextCheckMillis = Int64(lastRefreshTimestamp * 1000.0) + oneDayMilliseconds
            try await repository.updateLastUpdateCheckDate(lastUpdateCheckDate: nextCheckMillis)
        }

        try await seedChangelogVersionIfUpgrading(
            repository: repository,
            userDefaults: userDefaults,
            hasMigratedDevices: hasMigratedDevices
        )

        legacyKeys.forEach(userDefaults.removeObject(forKey:))
        userDefaults.set(true, forKey: migrationKey)

        logger.info("UserDefaults to DataStore preferences migration completed successfully.")
    }

    private static func seedChangelogVersionIfUpgrading(
        repository: UserPreferencesRepository,
        userDefaults: UserDefaults,
        hasMigratedDevices: Bool
    ) async throws {
        let hasLegacyPrefs = legacyKeys.contains { userDefaults.object(forKey: $0) != nil }
        guard hasMigratedDevices || hasLegacyPrefs else {
            return
        }

        let existingVersion = await repository.lastChangelogVersionSeen.firstValue() ?? ""
        if existingVersion.isEmpty {
            try await repository.updateLastChangelogVersionSeen(version: preChangelogBaselineVersion)
        }
    }
}
