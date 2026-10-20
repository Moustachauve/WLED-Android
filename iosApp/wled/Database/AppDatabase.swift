import Foundation
import OSLog
import Shared

final class AppDatabase: Sendable {
    static let shared = AppDatabase()
    // Bumped to _v2 after migrating releases from Core Data to Room so upgrading users fetch immediately.
    static let dateLastUpdateKey = "lastUpdateReleasesDate_v2"

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "AppDatabase"
    )

    let database: DevicesDatabase
    let deviceRepository: DeviceRepository
    // Computed because `any RepositoryDao` (an Obj-C protocol existential) is not Sendable in Swift 6;
    // Room's generated `database.repositoryDao()` returns a lazy singleton instance.
    var repositoryDao: any RepositoryDao { database.repositoryDao() }
    let versionWithAssetsRepository: VersionWithAssetsRepository
    let releaseService: ReleaseService
    let deviceUpdateManager: DeviceUpdateManager
    let githubApi: GithubApi
    let deviceApiFactory: DeviceApiFactory
    let deviceFirstContactService: DeviceFirstContactService
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
        let db: DevicesDatabase
        if inMemory {
            db = DevicesDatabase.companion.createInMemoryDatabase()
        } else {
            db = DevicesDatabase.companion.createDatabase()
        }
        self.database = db

        let repository = db.createDeviceRepository()
        self.deviceRepository = repository

        let repoDao = db.repositoryDao()

        let versionRepo = db.createVersionWithAssetsRepository()
        self.versionWithAssetsRepository = versionRepo

        let relService = ReleaseService(
            versionWithAssetsRepository: versionRepo,
            repositoryDao: repoDao
        )
        self.releaseService = relService
        let updateManager = DeviceUpdateManager(releaseService: relService)
        self.deviceUpdateManager = updateManager
        self.githubApi = GithubApi.companion.create()
        let apiFactory = deviceApiFactory ?? DeviceApiFactory.companion.create()
        self.deviceApiFactory = apiFactory
        self.deviceFirstContactService = DeviceFirstContactService(
            repositoryDao: repoDao,
            repository: repository,
            deviceApiFactory: apiFactory
        )

        let effectiveConnectionFactory = connectionFactory ?? (inMemory ? NoOpDeviceConnectionFactory.shared : nil)
        self.deviceConnectionManager = DeviceConnectionManager.companion.create(
            database: db,
            connectionFactory: effectiveConnectionFactory,
            config: config,
            updateTagResolver: updateManager
        )

        self.readyTask = Task {
            if !inMemory {
                do {
                    try await CoreDataToRoomMigrator.migrateIfNeeded(
                        repository: repository
                    )
                } catch {
                    Self.logger.error("Core Data to Room migration failed: \(error.localizedDescription)")
                }
            }
            do {
                try await relService.syncBuiltInRepositories()
            } catch {
                Self.logger.error("Failed to sync built-in repositories: \(error.localizedDescription)")
            }
        }
    }

    deinit {
        deviceConnectionManager.destroy()
    }

    /// Suspends until the database is ready to be read from and written to.
    /// Callers that observe or upsert devices must await this so they don't race the legacy import.
    func waitUntilReady() async {
        await readyTask.value
    }

    /// Refreshes available GitHub releases for all repositories used by known devices,
    /// throttled to at most once every 24 hours.
    func refreshVersionsIfNeeded(userDefaults: UserDefaults = .standard) async {
        await waitUntilReady()
        let lastRefreshTimestamp = userDefaults.double(forKey: Self.dateLastUpdateKey)
        if lastRefreshTimestamp > 0 {
            let lastRefreshDate = Date(timeIntervalSince1970: lastRefreshTimestamp)
            if let nextRefreshDate = Calendar.current.date(byAdding: .day, value: 1, to: lastRefreshDate),
               Date() <= nextRefreshDate {
                return
            }
        }
        Self.logger.info("Refreshing available releases from GitHub")
        do {
            let allSucceeded = try await releaseService.refreshVersionsForUsedRepositories(
                githubApi: githubApi,
                deviceRepository: deviceRepository
            )
            if allSucceeded.boolValue {
                deviceConnectionManager.recheckUpdates()
                userDefaults.set(Date().timeIntervalSince1970, forKey: Self.dateLastUpdateKey)
            }
        } catch {
            Self.logger.error("Failed to refresh releases: \(error.localizedDescription)")
        }
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

extension DeviceRepository: @retroactive @unchecked Sendable {}
extension VersionWithAssetsRepository: @retroactive @unchecked Sendable {}
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
