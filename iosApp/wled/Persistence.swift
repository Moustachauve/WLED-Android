import CoreData

struct PersistenceController {
    static let shared = PersistenceController()
    
    static let preview: PersistenceController = {
        let result = PersistenceController(inMemory: true)
        return result
    }()
    
    let container: NSPersistentContainer
    
    init(inMemory: Bool = false) {
        container = NSPersistentContainer(name: "wled_native_data")
        if inMemory {
            container.persistentStoreDescriptions.first?.url = URL(fileURLWithPath: "/dev/null")
        }
        container.loadPersistentStores(completionHandler: { (_, error) in
            if let error = error as NSError? {
                // MARK: - Enhanced Error Logging

                // Create a readable error message starting with the main error
                var errorMsg = "CORE DATA ERROR: \(error.localizedDescription)"

                // Define a helper to recursively dig for the "real" cause
                func appendDetails(from nsError: NSError, depth: Int = 1) -> String {
                    var extraInfo = ""
                    let indent = String(repeating: "  ", count: depth)

                    // Check for a single underlying error (common in migration failures)
                    if let underlying = nsError.userInfo[NSUnderlyingErrorKey] as? NSError {
                        extraInfo += "\n\(indent)Cause: \(underlying.localizedDescription)"
                        extraInfo += appendDetails(from: underlying, depth: depth + 1)
                    }

                    // Check for multiple detailed errors (common in validation failures)
                    if let detailedErrors = nsError.userInfo[NSDetailedErrorsKey] as? [NSError] {
                        for (index, detail) in detailedErrors.enumerated() {
                            extraInfo += "\n\(indent)Detail #\(index + 1): \(detail.localizedDescription)"
                            extraInfo += appendDetails(from: detail, depth: depth + 1)
                        }
                    }

                    // Append specific migration failure reasons if present
                    if let storeURL = nsError.userInfo[NSPersistentStoreURLKey] as? URL {
                        extraInfo += "\n\(indent)Store URL: \(storeURL.path)"
                    }

                    return extraInfo
                }

                // Append the details
                errorMsg += appendDetails(from: error)

                // Print full details to console (captured in system logs)
                print(errorMsg)
                print("Full UserInfo: \(error.userInfo)")

                // Crash with the detailed message. This ensures the "Last
                // Exception Backtrace" in Xcode/TestFlight contains the
                // readable reason.
                fatalError(errorMsg)
            }
        })
        container.viewContext.automaticallyMergesChangesFromParent = true
        container.viewContext.mergePolicy = NSMergePolicy.mergeByPropertyObjectTrump
        let description = container.persistentStoreDescriptions.first
        description?.shouldMigrateStoreAutomatically = true
        description?.shouldInferMappingModelAutomatically = true
    }
}
