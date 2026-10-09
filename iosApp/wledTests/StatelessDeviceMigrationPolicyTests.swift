import Testing
import CoreData
@testable import WLED

struct StatelessDeviceMigrationPolicyTests {

    let sourceModel: NSManagedObjectModel
    let destinationModel: NSManagedObjectModel
    let policy: StatelessDeviceMigrationPolicy
    let manager: MockMigrationManager
    let mapping: NSEntityMapping
    let sourceContext: NSManagedObjectContext

    init() {
        sourceModel = Self.createSourceModel()
        destinationModel = Self.createDestinationModel()

        policy = StatelessDeviceMigrationPolicy()
        manager = MockMigrationManager(sourceModel: sourceModel, destinationModel: destinationModel)
        mapping = NSEntityMapping()

        sourceContext = NSManagedObjectContext(concurrencyType: .mainQueueConcurrencyType)
        sourceContext.persistentStoreCoordinator = NSPersistentStoreCoordinator(managedObjectModel: sourceModel)
    }

    @Test
    func validMigration() throws {
        // Create valid source object
        let sourceEntity = try #require(sourceModel.entitiesByName["Device"])
        let sInstance = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
        sInstance.setValue("00:11:22:33:44:55", forKey: "macAddress")
        sInstance.setValue("192.168.1.100", forKey: "address")
        sInstance.setValue(false, forKey: "isCustomName")
        sInstance.setValue("My Light", forKey: "name")
        sInstance.setValue(true, forKey: "isHidden")
        sInstance.setValue("v1.0", forKey: "skipUpdateTag")

        try policy.createDestinationInstances(forSource: sInstance, in: mapping, manager: manager)

        #expect(manager.createdDestinations.count == 1)
        let dInstance = try #require(manager.createdDestinations.first)

        #expect(dInstance.value(forKey: "macAddress") as? String == "00:11:22:33:44:55")
        #expect(dInstance.value(forKey: "address") as? String == "192.168.1.100")
        #expect(dInstance.value(forKey: "isHidden") as? Bool == true)
        #expect(dInstance.value(forKey: "skipUpdateTag") as? String == "v1.0")

        // Verify Name Logic for non-custom name
        #expect(dInstance.value(forKey: "customName") == nil)
        #expect(dInstance.value(forKey: "originalName") as? String == "My Light")

        // Verify Defaults
        #expect(dInstance.value(forKey: "branch") as? String == Branch.unknown.rawValue)
        #expect(dInstance.value(forKey: "lastSeen") as? Int == 0)

        // Verify Association
        #expect(manager.associateCalled)
    }

    @Test
    func validMigrationWithCustomName() throws {
        // Create valid source object with custom name
        let sourceEntity = try #require(sourceModel.entitiesByName["Device"])
        let sInstance = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
        sInstance.setValue("AA:BB:CC:DD:EE:FF", forKey: "macAddress")
        sInstance.setValue(true, forKey: "isCustomName")
        sInstance.setValue("Custom Light", forKey: "name")

        try policy.createDestinationInstances(forSource: sInstance, in: mapping, manager: manager)

        #expect(manager.createdDestinations.count == 1)
        let dInstance = try #require(manager.createdDestinations.first)

        // Verify Name Logic for custom name
        #expect(dInstance.value(forKey: "customName") as? String == "Custom Light")
        #expect(dInstance.value(forKey: "originalName") == nil)
    }

    @Test
    func emptyMacAddress() throws {
        let sourceEntity = try #require(sourceModel.entitiesByName["Device"])
        let sInstance = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
        sInstance.setValue("", forKey: "macAddress")

        try policy.createDestinationInstances(forSource: sInstance, in: mapping, manager: manager)
        #expect(manager.createdDestinations.isEmpty)
    }

    @Test
    func unknownMacAddress() throws {
        let sourceEntity = try #require(sourceModel.entitiesByName["Device"])
        let sInstance = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
        sInstance.setValue("__unknown__", forKey: "macAddress")

        try policy.createDestinationInstances(forSource: sInstance, in: mapping, manager: manager)
        #expect(manager.createdDestinations.isEmpty)
    }

    @Test
    func missingMacAddress() throws {
         let sourceEntity = try #require(sourceModel.entitiesByName["Device"])
         let sInstance = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
         sInstance.setValue(nil, forKey: "macAddress")

         try policy.createDestinationInstances(forSource: sInstance, in: mapping, manager: manager)
         #expect(manager.createdDestinations.isEmpty)
     }

    @Test
    func duplicateMacAddress() throws {
        let mac = "00:11:22:33:44:55"
        let sourceEntity = try #require(sourceModel.entitiesByName["Device"])

        // First Object
        let sInstance1 = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
        sInstance1.setValue(mac, forKey: "macAddress")
        sInstance1.setValue("Name 1", forKey: "name")

        // Second Object (Duplicate)
        let sInstance2 = NSManagedObject(entity: sourceEntity, insertInto: sourceContext)
        sInstance2.setValue(mac, forKey: "macAddress")
        sInstance2.setValue("Name 2", forKey: "name")

        // Migrate first instance
        try policy.createDestinationInstances(forSource: sInstance1, in: mapping, manager: manager)
        #expect(manager.createdDestinations.count == 1)

        // Migrate second instance
        try policy.createDestinationInstances(forSource: sInstance2, in: mapping, manager: manager)
        #expect(manager.createdDestinations.count == 1, "Should not create a second instance for duplicate MAC")
    }

    // MARK: - Helpers

    static func createSourceModel() -> NSManagedObjectModel {
        let model = NSManagedObjectModel()

        // Based on wled_native_data.xcdatamodel (the older one likely, or the one we are migrating FROM)
        // We assume the source has attributes we are reading from.
        let deviceEntity = NSEntityDescription()
        deviceEntity.name = "Device"
        deviceEntity.managedObjectClassName = "NSManagedObject" // Use generic for test

        let macAttr = NSAttributeDescription()
        macAttr.name = "macAddress"
        macAttr.attributeType = .stringAttributeType
        macAttr.isOptional = true

        let addrAttr = NSAttributeDescription()
        addrAttr.name = "address"
        addrAttr.attributeType = .stringAttributeType

        let isCustomNameAttr = NSAttributeDescription()
        isCustomNameAttr.name = "isCustomName"
        isCustomNameAttr.attributeType = .booleanAttributeType

        let nameAttr = NSAttributeDescription()
        nameAttr.name = "name"
        nameAttr.attributeType = .stringAttributeType

        let isHiddenAttr = NSAttributeDescription()
        isHiddenAttr.name = "isHidden"
        isHiddenAttr.attributeType = .booleanAttributeType

        let skipAttr = NSAttributeDescription()
        skipAttr.name = "skipUpdateTag"
        skipAttr.attributeType = .stringAttributeType

        deviceEntity.properties = [macAttr, addrAttr, isCustomNameAttr, nameAttr, isHiddenAttr, skipAttr]
        model.entities = [deviceEntity]

        return model
    }

    static func createDestinationModel() -> NSManagedObjectModel {
        let model = NSManagedObjectModel()

        // Based on v2.xcdatamodel (The target of migration)
        let deviceEntity = NSEntityDescription()
        deviceEntity.name = "Device"
        deviceEntity.managedObjectClassName = "NSManagedObject"

        let macAttr = NSAttributeDescription()
        macAttr.name = "macAddress"
        macAttr.attributeType = .stringAttributeType

        let addrAttr = NSAttributeDescription()
        addrAttr.name = "address"
        addrAttr.attributeType = .stringAttributeType

        let customNameAttr = NSAttributeDescription()
        customNameAttr.name = "customName"
        customNameAttr.attributeType = .stringAttributeType

        let originalNameAttr = NSAttributeDescription()
        originalNameAttr.name = "originalName"
        originalNameAttr.attributeType = .stringAttributeType

        let isHiddenAttr = NSAttributeDescription()
        isHiddenAttr.name = "isHidden"
        isHiddenAttr.attributeType = .booleanAttributeType

        let skipAttr = NSAttributeDescription()
        skipAttr.name = "skipUpdateTag"
        skipAttr.attributeType = .stringAttributeType

        let branchAttr = NSAttributeDescription()
        branchAttr.name = "branch"
        branchAttr.attributeType = .stringAttributeType

        let lastSeenAttr = NSAttributeDescription()
        lastSeenAttr.name = "lastSeen"
        lastSeenAttr.attributeType = .integer64AttributeType

        deviceEntity.properties = [macAttr, addrAttr, customNameAttr, originalNameAttr, isHiddenAttr, skipAttr, branchAttr, lastSeenAttr]
        model.entities = [deviceEntity]

        return model
    }
}

class MockMigrationManager: NSMigrationManager {
    var associateCalled = false
    var createdDestinations: [NSManagedObject] {
        return Array(_destinationContext.insertedObjects)
    }

    // We need a context that works.
    private let _destinationContext: NSManagedObjectContext

    override var destinationContext: NSManagedObjectContext {
        return _destinationContext
    }

    override init(sourceModel: NSManagedObjectModel, destinationModel: NSManagedObjectModel) {
        let psc = NSPersistentStoreCoordinator(managedObjectModel: destinationModel)
        do {
            try psc.addPersistentStore(ofType: NSInMemoryStoreType, configurationName: nil, at: nil, options: nil)
        } catch {
            fatalError("Failed to add in-memory store: \(error)")
        }
        _destinationContext = NSManagedObjectContext(concurrencyType: .mainQueueConcurrencyType)
        _destinationContext.persistentStoreCoordinator = psc
        super.init(sourceModel: sourceModel, destinationModel: destinationModel)
    }

    override func associate(sourceInstance: NSManagedObject, withDestinationInstance destinationInstance: NSManagedObject, for mapping: NSEntityMapping) {
        associateCalled = true
    }
}
