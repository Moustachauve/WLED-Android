import Foundation
import Shared

final class AppDatabase: @unchecked Sendable {
    static let shared = AppDatabase()

    let database: DevicesDatabase
    let deviceRepository: DeviceRepository

    init(inMemory: Bool = false) {
        if inMemory {
            self.database = DevicesDatabase.companion.createInMemoryDatabase()
        } else {
            self.database = DevicesDatabase.companion.createDatabase()
        }
        self.deviceRepository = database.deviceRepository
    }
}

extension DeviceRepository: @retroactive @unchecked Sendable {}
extension Device: @retroactive @unchecked Sendable {}
extension DevicesDatabase: @retroactive @unchecked Sendable {}
