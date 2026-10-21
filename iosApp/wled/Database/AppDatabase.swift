import Foundation
import OSLog
import Shared

final class AppDatabase: Sendable {
    static let shared = AppDatabase()
    private static let oneDayMilliseconds: Int64 = 24 * 60 * 60 * 1000

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "AppDatabase"
    )

    private let koinContainer: KoinContainer
    let database: DevicesDatabase
    let deviceRepository: DeviceRepository
    // Computed because `any RepositoryDao` (an Obj-C protocol existential) is not Sendable in Swift 6;
    // Koin's `repositoryDao` binding returns a singleton instance.
    var repositoryDao: any RepositoryDao { koinContainer.repositoryDao }
    let versionWithAssetsRepository: VersionWithAssetsRepository
    let userPreferencesRepository: UserPreferencesRepository
    let releaseService: ReleaseService
    let deviceUpdateManager: DeviceUpdateManager
    let githubApi: GithubApi
    let deviceApiFactory: DeviceApiFactory
    let deviceFirstContactService: DeviceFirstContactService
    let addressValidator: AddressValidator
    let deviceConnectionManager: DeviceConnectionManager

    /// Completes once legacy Core Data devices have been imported and built-in repositories synced into Room.
    private let readyTask: Task<Void, Never>

    /// - Parameter inMemory: Uses a throwaway database and skips the legacy Core Data import (tests, previews).
    init(
        inMemory: Bool = false,
        connectionFactory: DeviceConnectionFactory? = nil,
        config: DeviceConnectionManagerConfig? = nil,
        deviceApiFactory: DeviceApiFactory? = nil
    ) {
        let container: KoinContainer
        if inMemory {
            container = KoinContainer.companion.inMemory(
                connectionFactory: connectionFactory,
                config: config,
                deviceApiFactory: deviceApiFactory
            )
        } else {
            container = KoinContainer.companion.production()
        }
        self.koinContainer = container
        self.database = container.database
        let repository = container.deviceRepository
        self.deviceRepository = repository
        self.versionWithAssetsRepository = container.versionWithAssetsRepository
        let userPrefsRepo = container.userPreferencesRepository
        self.userPreferencesRepository = userPrefsRepo
        let relService = container.releaseService
        self.releaseService = relService
        self.deviceUpdateManager = container.deviceUpdateManager
        self.githubApi = container.githubApi
        self.deviceApiFactory = container.deviceApiFactory
        self.deviceFirstContactService = container.deviceFirstContactService
        self.addressValidator = container.addressValidator
        self.deviceConnectionManager = container.deviceConnectionManager

        self.readyTask = Task {
            if !inMemory {
                do {
                    try await CoreDataToRoomMigrator.migrateIfNeeded(
                        repository: repository
                    )
                } catch is CancellationError {
                    return
                } catch {
                    Self.logger.error("Core Data to Room migration failed: \(error.localizedDescription)")
                }

                do {
                    try await UserDefaultsToDataStoreMigrator.migrateIfNeeded(
                        repository: userPrefsRepo
                    )
                } catch is CancellationError {
                    return
                } catch {
                    Self.logger.error("UserDefaults to DataStore migration failed: \(error.localizedDescription)")
                }
            }
            do {
                try await relService.syncBuiltInRepositories()
            } catch is CancellationError {
                return
            } catch {
                Self.logger.error("Failed to sync built-in repositories: \(error.localizedDescription)")
            }
        }
    }

    deinit {
        readyTask.cancel()
        koinContainer.close()
    }

    /// Suspends until the database is ready to be read from and written to.
    /// Callers that observe or upsert devices must await this so they don't race the legacy import.
    func waitUntilReady() async {
        await readyTask.value
    }

    /// Refreshes available GitHub releases for all repositories used by known devices,
    /// throttled to at most once every 24 hours.
    func refreshVersionsIfNeeded() async {
        await waitUntilReady()
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000.0)
        if let lastCheckDate = await firstValue(from: userPreferencesRepository.lastUpdateCheckDate),
           nowMillis < lastCheckDate.int64Value {
            return
        }
        Self.logger.info("Refreshing available releases from GitHub")
        do {
            let allSucceeded = try await releaseService.refreshVersionsForUsedRepositories(
                githubApi: githubApi,
                deviceRepository: deviceRepository
            )
            if allSucceeded.boolValue {
                deviceConnectionManager.recheckUpdates()
                try await userPreferencesRepository.updateLastUpdateCheckDate(
                    lastUpdateCheckDate: nowMillis + Self.oneDayMilliseconds
                )
            }
        } catch {
            Self.logger.error("Failed to refresh releases: \(error.localizedDescription)")
        }
    }

    private func firstValue<S: AsyncSequence>(from sequence: S) async -> S.Element? {
        do {
            for try await value in sequence {
                return value
            }
        } catch {}
        return nil
    }

    /// Forces a refresh of GitHub releases for the repository associated with `device`.
    func refreshVersions(for device: Device) async {
        await waitUntilReady()
        Self.logger.info("Refreshing available releases for device \(device.macAddress)")
        do {
            _ = try await releaseService.refreshVersionsForDevice(
                githubApi: githubApi,
                device: device
            )
            deviceConnectionManager.recheckUpdates()
        } catch {
            Self.logger.error("Failed to refresh releases for device: \(error.localizedDescription)")
        }
    }
}

extension KoinContainer: @retroactive @unchecked Sendable {}
extension DeviceRepository: @retroactive @unchecked Sendable {}
extension VersionWithAssetsRepository: @retroactive @unchecked Sendable {}
extension UserPreferencesRepository: @retroactive @unchecked Sendable {}
extension ReleaseService: @retroactive @unchecked Sendable {}
extension DeviceUpdateManager: @retroactive @unchecked Sendable {}
extension GithubApi: @retroactive @unchecked Sendable {}
extension DeviceApiFactory: @retroactive @unchecked Sendable {}
extension Device: @retroactive @unchecked Sendable {}
extension DeviceStateInfo: @retroactive @unchecked Sendable {}
extension VersionWithAssets: @retroactive @unchecked Sendable {}
extension Version: @retroactive @unchecked Sendable {}
extension Asset: @retroactive @unchecked Sendable {}
extension Repository: @retroactive @unchecked Sendable {}
extension DevicesDatabase: @retroactive @unchecked Sendable {}
extension DeviceConnectionManager: @retroactive @unchecked Sendable {}
extension DeviceUpdateService: @retroactive @unchecked Sendable {}
extension DeviceFirstContactService: @retroactive @unchecked Sendable {}
extension AddressValidator: @retroactive @unchecked Sendable {}
extension ThemeSettings: @retroactive @unchecked Sendable {}
