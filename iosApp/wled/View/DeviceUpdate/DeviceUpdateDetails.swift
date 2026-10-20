import SwiftUI
import Shared
import MarkdownUI
import OSLog

private typealias State = SwiftUI.State

struct DeviceUpdateDetails: View {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceUpdateDetails"
    )
    // TODO: Pass the version to display instead of only showing the latest one
    // This will allow support for downgrading or chosing a different version
    // in the future.
    @Environment(\.dismiss) var dismiss
    var device: DeviceWithState
    var database: AppDatabase = .shared

    @State var showWarningDialog = false
    @State var showInstallingDialog = false
    @State private var versionWithAssets: VersionWithAssets?

    var body: some View {
        ZStack {
            ScrollView {
                Markdown(versionWithAssets?.version.descriptionText ?? String(localized: "[Unknown Error]"))
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
                .disabled(!device.isOnline || versionWithAssets == nil)
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
        .navigationTitle("Version \(versionWithAssets?.version.tagName ?? device.availableUpdateVersion ?? "")")
        .fullScreenCover(isPresented: $showInstallingDialog) {
            if let versionWithAssets {
                DeviceUpdateInstalling(
                    device: device,
                    version: versionWithAssets,
                    database: database
                )
                .background(BackgroundBlurView())
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: .didCompleteUpdateInstall)) { _ in
            dismiss()
        }
        .task(id: device.availableUpdateVersion) {
            await loadVersion()
        }
    }

    private func loadVersion() async {
        guard let tag = device.availableUpdateVersion, !tag.isEmpty else {
            return
        }
        await database.waitUntilReady()
        do {
            versionWithAssets = try await database.versionWithAssetsRepository.getVersionByTag(
                repositoryId: device.device.repositoryId,
                tagName: tag
            )
        } catch {
            versionWithAssets = nil
            Self.logger.error("Unexpected error when loading version \(tag): \(error.localizedDescription)")
        }
    }

    func skipVersion() {
        let tag = device.availableUpdateVersion ?? ""
        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: tag)
        device.availableUpdateVersion = nil
        let repository = database.deviceRepository
        Task {
            do {
                try await repository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: tag)
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
        DeviceUpdateDetails(
            device: PreviewData.deviceWithUpdate,
            database: PreviewData.database
        )
    }
}
