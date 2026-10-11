import Foundation
import CoreData
import Shared
import OSLog

struct CoreDataToRoomMigrator {
    static let migrationKey = "hasCompletedCoreDataToRoomMigration_v1"
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "CoreDataToRoomMigrator"
    )

    static func migrateIfNeeded(
        context: NSManagedObjectContext,
        repository: DeviceRepository,
        userDefaults: UserDefaults = .standard
    ) async throws {
        guard !userDefaults.bool(forKey: migrationKey) else {
            return
        }

        logger.info("Starting Core Data to Room device migration...")
        let request = NSFetchRequest<NSManagedObject>(entityName: "Device")

        let devices: [Device] = try await context.perform {
            try context.fetch(request).compactMap(makeDevice(from:))
        }

        logger.info("Found \(devices.count) device(s) in Core Data to migrate.")
        for device in devices {
            try await repository.insert(device: device)
            logger.debug("Migrated device \(device.macAddress) (\(device.originalName)) to Room.")
        }

        userDefaults.set(true, forKey: migrationKey)
        logger.info("Core Data to Room device migration completed successfully.")
    }

    /// Maps a legacy Core Data device to a Room device. Must be called on the managed object's context queue.
    private static func makeDevice(from managedDevice: NSManagedObject) -> Device? {
        guard let macAddress = managedDevice.value(forKey: "macAddress") as? String,
              !macAddress.isEmpty,
              macAddress != "__unknown__" else {
            return nil
        }
        return Device(
            macAddress: macAddress,
            address: managedDevice.value(forKey: "address") as? String ?? "",
            isHidden: managedDevice.value(forKey: "isHidden") as? Bool ?? false,
            originalName: managedDevice.value(forKey: "originalName") as? String ?? "",
            customName: managedDevice.value(forKey: "customName") as? String ?? "",
            skipUpdateTag: managedDevice.value(forKey: "skipUpdateTag") as? String ?? "",
            branch: Branch(legacyValue: managedDevice.value(forKey: "branch") as? String ?? ""),
            lastSeen: managedDevice.value(forKey: "lastSeen") as? Int64 ?? 0
        )
    }
}
