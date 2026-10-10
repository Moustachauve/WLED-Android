import SwiftUI

struct DeviceUpdateInstalling: View {
    @Environment(\.presentationMode) var presentationMode

    @StateObject var viewModel = DeviceUpdateInstallingViewModel()
    @ObservedObject var device: DeviceWithState
    @ObservedObject var version: Version

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
                    IndeterminateView(statusString: String(localized: "Starting Up"))
                case .downloading(let versionName):
                    IndeterminateView(
                        statusString: String(localized: "Downloading Version"),
                        versionName: versionName
                    )
                case .installing(let versionName):
                    IndeterminateView(
                        statusString: String(localized: "Installing Update"),
                        versionName: versionName
                    )
                case .completed:
                    SuccessView()
                case .failed(let error, let versionName):
                    FailureView(errorMessage: error, versionName: versionName)

                }

                Button {
                    NotificationCenter.default.post(
                        name: .didCompleteUpdateInstall,
                        object: nil
                    )
                    presentationMode.wrappedValue.dismiss()
                } label: {
                    Text(dismissButtonText)
                        .buttonStyle(.plain)
                }
                .disabled(!canDismiss)
                .padding(.top)
            }
            .fixedSize(horizontal: false, vertical: /*@START_MENU_TOKEN@*/true/*@END_MENU_TOKEN@*/)
            .padding()
            .background(Color(UIColor.secondarySystemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .shadow(radius: 20)
            .task {
                await viewModel
                    .startUpdateProcess(device: device, version: version)
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

// MARK: - Indeterminate view

struct IndeterminateView: View {
    let statusString: String
    var versionName: String = ""

    var body: some View {
        ProgressView()
            .controlSize(.large)
            .padding(.bottom, 5)

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

#Preview {
    let device = PreviewData.deviceWithUpdate
    let version = Version(context: PreviewData.viewContext)
    
    return DeviceUpdateInstalling(device: device, version: version)
}
