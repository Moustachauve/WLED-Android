import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceEditView: View {
    @State private var viewModel: DeviceEditViewModel
    private var device: DeviceWithState
    private let database: AppDatabase

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared
    ) {
        _viewModel = State(wrappedValue: DeviceEditViewModel(
            device: device,
            database: database
        ))

        self.device = device
        self.database = database
    }

    // MARK: - body

    var body: some View {
        ScrollView {
            VStack(spacing: 10) {
                Card(style: .device(color: device.currentColor)) {
                    DeviceInfoTwoRows(device: device)
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 8, bottom: 4, trailing: 8))

                VStack(alignment: .leading) {
                    Text("Custom Name")
                    TextField("Custom Name", text: $viewModel.customName)
                        .submitLabel(.done)
                        .textFieldStyle(RoundedBorderTextFieldStyle())
                }

                Toggle("Hide this Device", isOn: $viewModel.hideDevice)
                    .padding(.trailing, 2)
                    .padding(.bottom)

                HStack {
                    Text("Update Channel")
                    Spacer()
                    Picker("Update Channel", selection: $viewModel.branch) {
                        ForEach(Branch.allCases.filter { $0 != .unknown }) { branch in
                            Text(LocalizedStringKey(branch.nameKey))
                                .tag(branch)
                                .padding()
                        }
                    }
                    .pickerStyle(.segmented)
                    .fixedSize()
                }
                .padding(.bottom)

                if device.stateInfo != nil {
                    Card {
                        if (device.availableUpdateVersion ?? "").isEmpty {
                            DeviceNoUpdateAvailable(
                                device: device,
                                isCheckingForUpdates: viewModel.isCheckingForUpdates
                            ) {
                                await viewModel.checkForUpdate()
                            }
                        } else {
                            DeviceUpdateAvailable(device: device, database: database)
                        }
                    }
                    .animation(.default, value: device.availableUpdateVersion)
                    .animation(.default, value: viewModel.isCheckingForUpdates)
                }

                Text("Mac Address: \(device.device.macAddress)")
                    .font(.caption)
                    .frame(maxWidth: .infinity, alignment: .leading)

                Spacer()
            }
            .padding()
            .padding(.bottom, 100)
        }
        .navigationTitle("Edit Device")
        .navigationBarTitleDisplayMode(.large)
    }
}

// MARK: - Device No Update Available

struct DeviceNoUpdateAvailable: View {

    var device: DeviceWithState
    let isCheckingForUpdates: Bool
    let onCheckForUpdate: () async -> Void

    var body: some View {
        Text("Your device is up to date")
        Text(
            "Version \(device.stateInfo?.info.version ?? String(localized: "unknown_version"))"
        )
        HStack {
            Button(
                action: {
                    Task {
                        await onCheckForUpdate()
                    }
                },
                label: {
                    Text(isCheckingForUpdates ? "Checking for Updates" : "Check for Update")
                }
            )
            .buttonStyle(.bordered)
            .padding(.trailing)
            .disabled(isCheckingForUpdates)
            ProgressView()
                .opacity(isCheckingForUpdates ? 1 : 0)
        }
    }
}

// MARK: - Device Update Available

struct DeviceUpdateAvailable: View {

    var device: DeviceWithState
    var database: AppDatabase = .shared

    private let unknownVersion = String(localized: "unknown_version")

    var body: some View {
        HStack {
            Image(systemName: getUpdateIconName())
                .resizable()
                // TODO: Migrate legacy aspectRatio to scaledToFill in a dedicated follow-up PR
                // swiftlint:disable:next all
                .aspectRatio(contentMode: .fill)
                .frame(width: 30.0, height: 30.0)
                .padding(.trailing)
            VStack(alignment: .leading) {
                Text("Update Available")
                Text("From \(device.stateInfo?.info.version ?? unknownVersion) to \(device.availableUpdateVersion ?? unknownVersion)")
                NavigationLink {
                    DeviceUpdateDetails(device: device, database: database)
                } label: {
                    Text("See Update")
                }
            }
        }
        .buttonStyle(.borderedProminent)
    }

    private func getUpdateIconName() -> String {
        "arrow.down.circle.dotted"
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceEditView(device: PreviewData.onlineDevice, database: PreviewData.database)
    }
}
#endif
