import SwiftUI

@main
struct WLEDNativeApp: App {
    static let dateLastUpdateKey = "lastUpdateReleasesDate"
    
    let persistenceController = PersistenceController.shared
    
    var body: some Scene {
        WindowGroup {
            DeviceListView()
                .environment(\.managedObjectContext, persistenceController.container.viewContext)
                .onAppear {
                    refreshVersionsSync()
                }
        }
    }
    
    private func refreshVersionsSync() {
        Task {
            // Only update automatically from Github once per 24 hours to avoid rate limits
            // and reduce network usage.
            let date = Date(timeIntervalSince1970: UserDefaults.standard.double(forKey: WLEDNativeApp.dateLastUpdateKey))
            var dateComponent = DateComponents()
            dateComponent.day = 1
            let dateToRefresh = Calendar.current.date(byAdding: dateComponent, to: date)
            let dateNow = Date()
            guard let dateToRefresh = dateToRefresh else {
                return
            }
            if dateNow <= dateToRefresh {
                return
            }
            print("Refreshing available Releases")
            await ReleaseService(context: persistenceController.container.viewContext).refreshVersions()
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: WLEDNativeApp.dateLastUpdateKey)
        }
    }
}
