import SwiftUI
import CoreData
import Shared
import MarkdownUI
import OSLog

private typealias State = SwiftUI.State

struct DeviceUpdateDetails: View {
    private static let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native", category: "DeviceUpdateDetails")
    // TODO: Pass the version to display instead of only showing the latest one
    // This will allow support for downgrading or chosing a different version
    // in the future.
    @Environment(\.managedObjectContext) private var viewContext
    @Environment(\.dismiss) var dismiss
    @ObservedObject var device: DeviceWithState
    
    @State var showWarningDialog = false
    @State var showInstallingDialog = false
    
    @StateObject var versionViewModel = VersionViewModel()
    
    var body: some View {
        ZStack {
            ScrollView {
                Markdown(versionViewModel.version?.versionDescription ?? String(localized: "[Unknown Error]"))
                    .padding()
            }
        }
        .safeAreaInset(edge: .bottom) {
            HStack {
                Button("Skip This Version") {
                    skipVersion()
                }
                
                Spacer()
                
                Button("Install") {
                    showWarningDialog = true
                }
                .buttonStyle(.borderedProminent)
                .disabled(!device.isOnline)
                .confirmationDialog("Are you sure?",
                                    isPresented: $showWarningDialog) {
                    Button("Install Now") {
                        installVersion()
                    }
                } message: {
                    Text("update_disclaimer")
                }
            }
            .padding()
            .background(.bar)
        }
        .navigationTitle("Version \(versionViewModel.version?.tagName ?? "")")
        .fullScreenCover(isPresented: $showInstallingDialog) {
            if let version = versionViewModel.version {
                DeviceUpdateInstalling(device: device, version: version)
                    .background(BackgroundBlurView())
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .didCompleteUpdateInstall)) {_ in
            dismiss()
        }
        .onAppear {
            versionViewModel
                .loadVersion(
                    device.availableUpdateVersion ?? "",
                    context: viewContext
                )
        }
    }
    
    func skipVersion() {
        let tag = device.availableUpdateVersion ?? ""
        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: tag)
        Task {
            do {
                try await AppDatabase.shared.deviceRepository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: tag)
            } catch {
                Self.logger.error("Unresolved error saving skip version: \(error.localizedDescription)")
            }
        }
        dismiss()
    }
    
    func installVersion() {
        showInstallingDialog = true
    }
    
}

#Preview {
    NavigationView {
        DeviceUpdateDetails(device: PreviewData.deviceWithUpdate)
    }
    // This line is required to provide the Core Data context to the view
    .environment(\.managedObjectContext, PreviewData.viewContext)
}
