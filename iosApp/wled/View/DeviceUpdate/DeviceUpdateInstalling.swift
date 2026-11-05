import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceUpdateInstalling: View {
    @Environment(\.dismiss) private var dismiss

    @State private var viewModel = DeviceUpdateInstallingViewModel()
    var device: DeviceWithState
    let version: VersionWithAssets
    var database: AppDatabase = .shared

    var body: some View {
        ZStack {
            Color(.clear)
            VStack {
                Text("Updating \(device.device.displayName)")
                    .font(.title2)
                    .bold()
                    .padding(.top)
                    .padding(.trailing)
                    .padding(.leading)

                switch viewModel.status {
                case .idle:
                    UpdateProgressStatusView(statusString: String(localized: "Starting Up"))
                case .downloading(let versionName, let progress):
                    UpdateProgressStatusView(
                        statusString: String(localized: "Downloading Version"),
                        versionName: versionName,
                        progress: progress
                    )
                case .installing(let versionName):
                    UpdateProgressStatusView(
                        statusString: String(localized: "Installing Update"),
                        versionName: versionName
                    )
                case .completed:
                    SuccessView()
                case .failed(let versionName, let error):
                    FailureView(errorMessage: error, versionName: versionName)
                }

                Button {
                    viewModel.completeUpdateIfInstalled(
                        device: device,
                        version: version,
                        database: database
                    )
                    NotificationCenter.default.post(
                        name: .didCompleteUpdateInstall,
                        object: nil
                    )
                    dismiss()
                } label: {
                    Text(dismissButtonText)
                        .buttonStyle(.plain)
                }
                .disabled(!canDismiss)
                .padding(.top)
            }
            .fixedSize(horizontal: false, vertical: true)
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .shadow(radius: 20)
            .task {
                await viewModel.startUpdateProcess(
                    device: device,
                    version: version,
                    database: database
                )
            }
        }
    }

    var canDismiss: Bool {
        switch viewModel.status {
        case .completed, .failed:
            return true
        default:
            return false
        }
    }

    var dismissButtonText: LocalizedStringKey {
        switch viewModel.status {
        case .completed, .failed:
            return LocalizedStringKey("Done")
        default:
            return LocalizedStringKey("Cancel")
        }
    }
}

// MARK: - Update progress status view

struct UpdateProgressStatusView: View {
    let statusString: String
    var versionName: String = ""
    var progress: Int?

    var body: some View {
        if let progress {
            let clampedProgress = min(max(progress, 0), 100)
            ProgressView(value: Double(clampedProgress), total: 100) {
                Text("\(clampedProgress)%")
                    .font(.caption)
                    .monospacedDigit()
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .progressViewStyle(.linear)
            .padding(.horizontal)
            .padding(.bottom, 5)
        } else {
            ProgressView()
                .controlSize(.large)
                .padding(.bottom, 5)
        }

        Text(statusString)
            .font(.title3)
            .bold()

        if !versionName.isEmpty {
            Text(versionName)
                .font(.callout)
        }

        Text("Please do not close the app or turn off the device.")
            .multilineTextAlignment(.center)
            .padding()
    }
}

// MARK: - Success view

struct SuccessView: View {
    var body: some View {
        Image(systemName: "checkmark.seal.fill")
            .resizable()
            .foregroundColor(.green)
            .frame(width: 32.0, height: 32.0)
            .padding(.bottom, 5)

        Text("Update Completed!")
            .font(.title3)
            .bold()
    }
}

// MARK: - Failure view

struct FailureView: View {
    let errorMessage: String
    var versionName: String = ""

    var body: some View {
        Image(systemName: "exclamationmark.octagon.fill")
            .resizable()
            .foregroundColor(.red)
            .frame(width: 32.0, height: 32.0)
            .padding(.bottom, 5)

        Text("Update Failed")
            .font(.title3)
            .bold()

        if !versionName.isEmpty {
            Text(versionName)
                .font(.callout)
        }

        Text(errorMessage)
            .multilineTextAlignment(.center)
            .padding()
    }
}

extension Notification.Name {
    static var didCompleteUpdateInstall: Notification.Name {
        return Notification.Name("did complete update install")
    }
}

#if DEBUG
#Preview {
    DeviceUpdateInstalling(
        device: PreviewData.deviceWithUpdate,
        version: PreviewData.previewVersion,
        database: PreviewData.database
    )
}
#endif
