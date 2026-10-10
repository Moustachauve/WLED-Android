import CoreData

@objc(StatelessDeviceMigrationPolicy)
class StatelessDeviceMigrationPolicy: NSEntityMigrationPolicy {

    // Keep track of MACs we have already processed during this migration
    var migratedMacAddresses = Set<String>()

    override func createDestinationInstances(forSource sInstance: NSManagedObject, in mapping: NSEntityMapping, manager: NSMigrationManager) throws {

        // Filter out invalid MAC addresses
        guard let macAddress = sInstance.value(forKey: "macAddress") as? String,
              !macAddress.isEmpty,
              macAddress != "__unknown__" else {
            // If we return without creating a destination instance,
            // this item is effectively dropped/deleted during migration.
            return
        }

        // Check for duplicates
        if migratedMacAddresses.contains(macAddress) {
            // We have already migrated a device with this MAC.
            // Skipping this instance prevents the uniqueness constraint violation crash.
            // (Optional: You could add logic here to merge data into the existing one if needed)
            NSLog("Migration warning: Dropping duplicate device with MAC: %@", macAddress)
            return
        }
        migratedMacAddresses.insert(macAddress)

        // Create the destination object (Device)
        let dInstance = NSEntityDescription.insertNewObject(forEntityName: "Device", into: manager.destinationContext)
        
        // 3. Copy direct mappings
        dInstance.setValue(macAddress, forKey: "macAddress")
        dInstance.setValue(sInstance.value(forKey: "address"), forKey: "address")
        dInstance.setValue(sInstance.value(forKey: "isHidden"), forKey: "isHidden")
        dInstance.setValue(sInstance.value(forKey: "skipUpdateTag"), forKey: "skipUpdateTag")
        
        // 4. Handle Name Logic
        let isCustomName = sInstance.value(forKey: "isCustomName") as? Bool ?? false
        let oldName = sInstance.value(forKey: "name") as? String ?? ""
        
        if isCustomName {
            dInstance.setValue(oldName, forKey: "customName")
            dInstance.setValue(nil, forKey: "originalName")
        } else {
            dInstance.setValue(nil, forKey: "customName")
            dInstance.setValue(oldName, forKey: "originalName")
        }
        
        // Set Defaults
        dInstance.setValue(Branch.unknown.rawValue, forKey: "branch")
        dInstance.setValue(0, forKey: "lastSeen")
        
        // 6. Associate the source and destination (Required)
        manager.associate(sourceInstance: sInstance, withDestinationInstance: dInstance, for: mapping)
    }
}
