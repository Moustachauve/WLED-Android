import Shared
import SwiftUI

@main
struct WLEDNativeApp: App {
    /// Set by xcodebuild when the app is launched as a unit test host. In that case the real UI is skipped so
    /// tests don't open live device connections, run discovery, or fetch releases from GitHub.
    private static let isRunningUnitTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    var body: some Scene {
        WindowGroup {
            if Self.isRunningUnitTests {
                EmptyView()
            } else {
                DeviceListView(database: .shared)
                    .task {
                        await AppDatabase.shared.refreshVersionsIfNeeded()
                    }
            }
        }
    }
}
