import Foundation
import CoreData

class VersionViewModel: ObservableObject {

    @Published var version: Version?

    func loadVersion(_ versionTag: String, context: NSManagedObjectContext) {
        let fetchRequest = Version.fetchRequest()
        fetchRequest.predicate = NSPredicate(format: "tagName == %@", versionTag)

        do {
            print("loading new version...")
            version = try context.fetch(fetchRequest).first
            print("Done loading version: \(version?.tagName ?? "nil")")
        } catch {
            print("Unexpected error when loading version: \(error)")
        }
    }
}
