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

        let managedDevices: [NSManagedObject] = try await context.perform {
            try context.fetch(request)
        }

        logger.info("Found \(managedDevices.count) device(s) in Core Data to migrate.")
        for managedDevice in managedDevices {
            guard let macAddress = managedDevice.value(forKey: "macAddress") as? String,
                  !macAddress.isEmpty,
                  macAddress != "__unknown__" else {
                continue
            }
            let address = managedDevice.value(forKey: "address") as? String ?? ""
            let isHidden = managedDevice.value(forKey: "isHidden") as? Bool ?? false
            let originalName = managedDevice.value(forKey: "originalName") as? String ?? ""
            let customName = managedDevice.value(forKey: "customName") as? String ?? ""
            let skipUpdateTag = managedDevice.value(forKey: "skipUpdateTag") as? String ?? ""
            let rawBranch = managedDevice.value(forKey: "branch") as? String ?? ""
            let lastSeen = managedDevice.value(forKey: "lastSeen") as? Int64 ?? 0

            let branch: Branch
            switch rawBranch.lowercased() {
            case "beta":
                branch = .beta
            case "stable":
                branch = .stable
            default:
                branch = .unknown
            }

            let device = Device(
                macAddress: macAddress,
                address: address,
                isHidden: isHidden,
                originalName: originalName,
                customName: customName,
                skipUpdateTag: skipUpdateTag,
                branch: branch,
                lastSeen: lastSeen
            )

            try await repository.insert(device: device)
            logger.debug("Migrated device \(macAddress) (\(originalName)) to Room.")
        }

        userDefaults.set(true, forKey: migrationKey)
        logger.info("Core Data to Room device migration completed successfully.")
    }
}
