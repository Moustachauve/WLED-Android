import Testing
import Foundation
import CoreData
import Shared
@testable import WLED

@MainActor
struct CoreDataToRoomMigratorTests {

    let container: NSPersistentContainer
    let context: NSManagedObjectContext
    let database: AppDatabase
    let repository: DeviceRepository
    let testUserDefaults: UserDefaults

    init() {
        self.container = PersistenceController(inMemory: true).container
        self.context = container.viewContext
        self.database = AppDatabase(inMemory: true)
        self.repository = database.deviceRepository

        let suiteName = "CoreDataToRoomMigratorTests.\(UUID().uuidString)"
        self.testUserDefaults = UserDefaults(suiteName: suiteName) ?? .standard
    }

    @Test func testSuccessfulMigration() async throws {
        // 1. Insert legacy devices into Core Data
        let managed1 = NSEntityDescription.insertNewObject(forEntityName: "Device", into: context)
        managed1.setValue("AA:BB:CC:DD:EE:01", forKey: "macAddress")
        managed1.setValue("192.168.1.101", forKey: "address")
        managed1.setValue(false, forKey: "isHidden")
        managed1.setValue("Living Room", forKey: "originalName")
        managed1.setValue("Custom Lamp", forKey: "customName")
        managed1.setValue("v0.14.0", forKey: "skipUpdateTag")
        managed1.setValue("stable", forKey: "branch")
        managed1.setValue(Int64(1700000000000), forKey: "lastSeen")

        let managed2 = NSEntityDescription.insertNewObject(forEntityName: "Device", into: context)
        managed2.setValue("AA:BB:CC:DD:EE:02", forKey: "macAddress")
        managed2.setValue("192.168.1.102", forKey: "address")
        managed2.setValue(true, forKey: "isHidden")
        managed2.setValue("Kitchen Strip", forKey: "originalName")
        managed2.setValue("", forKey: "customName")
        managed2.setValue("", forKey: "skipUpdateTag")
        managed2.setValue("beta", forKey: "branch")
        managed2.setValue(Int64(1700000005000), forKey: "lastSeen")

        try context.save()

        // 2. Run migration
        try await CoreDataToRoomMigrator.migrateIfNeeded(
            context: context,
            repository: repository,
            userDefaults: testUserDefaults
        )

        // 3. Verify flag set
        #expect(testUserDefaults.bool(forKey: CoreDataToRoomMigrator.migrationKey))

        // 4. Verify Room devices
        let allDevices = try await repository.getAllDevices()
        #expect(allDevices.count == 2)

        let d1 = try await repository.findDeviceByMacAddress(address: "AA:BB:CC:DD:EE:01")
        #expect(d1 != nil)
        #expect(d1?.address == "192.168.1.101")
        #expect(d1?.isHidden == false)
        #expect(d1?.originalName == "Living Room")
        #expect(d1?.customName == "Custom Lamp")
        #expect(d1?.skipUpdateTag == "v0.14.0")
        #expect(d1?.branch == .stable)
        #expect(d1?.lastSeen == 1700000000000)

        let d2 = try await repository.findDeviceByMacAddress(address: "AA:BB:CC:DD:EE:02")
        #expect(d2 != nil)
        #expect(d2?.address == "192.168.1.102")
        #expect(d2?.isHidden == true)
        #expect(d2?.originalName == "Kitchen Strip")
        #expect(d2?.branch == .beta)
        #expect(d2?.lastSeen == 1700000005000)
    }

    @Test func testAlreadyMigratedDoesNotRunAgain() async throws {
        testUserDefaults.set(true, forKey: CoreDataToRoomMigrator.migrationKey)

        let managed = NSEntityDescription.insertNewObject(forEntityName: "Device", into: context)
        managed.setValue("AA:BB:CC:DD:EE:99", forKey: "macAddress")
        managed.setValue("192.168.1.199", forKey: "address")
        try context.save()

        try await CoreDataToRoomMigrator.migrateIfNeeded(
            context: context,
            repository: repository,
            userDefaults: testUserDefaults
        )

        let allDevices = try await repository.getAllDevices()
        #expect(allDevices.isEmpty)
    }

    @Test func testSkipsInvalidAndUnknownMacAddresses() async throws {
        let invalid1 = NSEntityDescription.insertNewObject(forEntityName: "Device", into: context)
        invalid1.setValue("", forKey: "macAddress")
        invalid1.setValue("192.168.1.50", forKey: "address")

        let invalid2 = NSEntityDescription.insertNewObject(forEntityName: "Device", into: context)
        invalid2.setValue("__unknown__", forKey: "macAddress")
        invalid2.setValue("192.168.1.51", forKey: "address")

        let valid = NSEntityDescription.insertNewObject(forEntityName: "Device", into: context)
        valid.setValue("AA:BB:CC:DD:EE:52", forKey: "macAddress")
        valid.setValue("192.168.1.52", forKey: "address")

        try context.save()

        try await CoreDataToRoomMigrator.migrateIfNeeded(
            context: context,
            repository: repository,
            userDefaults: testUserDefaults
        )

        let allDevices = try await repository.getAllDevices()
        #expect(allDevices.count == 1)
        #expect(allDevices.first?.macAddress == "AA:BB:CC:DD:EE:52")
    }
}
