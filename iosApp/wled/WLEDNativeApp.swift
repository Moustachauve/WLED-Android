import Shared
import SwiftUI

@main
struct WLEDNativeApp: App {
    static let dateLastUpdateKey = "lastUpdateReleasesDate"

    /// Set by xcodebuild when the app is launched as a unit test host. In that case the real UI is skipped so
    /// tests don't open live device connections, run discovery, or fetch releases from GitHub.
    private static let isRunningUnitTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    let persistenceController = PersistenceController.shared

    var body: some Scene {
        WindowGroup {
            if Self.isRunningUnitTests {
                EmptyView()
            } else {
                DeviceListView(database: .shared)
                    .environment(\.managedObjectContext, persistenceController.container.viewContext)
                    .onAppear {
                        refreshVersionsSync()
                    }
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
