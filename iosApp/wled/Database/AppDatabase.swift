import Foundation
import CoreData
import OSLog
import Shared

final class AppDatabase: @unchecked Sendable {
    static let shared = AppDatabase()

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "AppDatabase"
    )

    let database: DevicesDatabase
    let deviceRepository: DeviceRepository

    /// Completes once legacy Core Data devices have been imported into Room.
    private let readyTask: Task<Void, Never>

    /// - Parameter inMemory: Uses a throwaway database and skips the legacy Core Data import (tests, previews).
    init(inMemory: Bool = false) {
        if inMemory {
            self.database = DevicesDatabase.companion.createInMemoryDatabase()
        } else {
            self.database = DevicesDatabase.companion.createDatabase()
        }
        let repository = database.createDeviceRepository()
        self.deviceRepository = repository

        if inMemory {
            self.readyTask = Task {}
        } else {
            self.readyTask = Task {
                do {
                    try await CoreDataToRoomMigrator.migrateIfNeeded(
                        context: PersistenceController.shared.container.newBackgroundContext(),
                        repository: repository
                    )
                } catch {
                    Self.logger.error("Core Data to Room migration failed: \(error.localizedDescription)")
                }
            }
        }
    }

    /// Suspends until the database is ready to be read from and written to.
    /// Callers that observe or upsert devices must await this so they don't race the legacy import.
    func waitUntilReady() async {
        await readyTask.value
    }
}

extension DeviceRepository: @retroactive @unchecked Sendable {}
extension Device: @retroactive @unchecked Sendable {}
extension DevicesDatabase: @retroactive @unchecked Sendable {}
