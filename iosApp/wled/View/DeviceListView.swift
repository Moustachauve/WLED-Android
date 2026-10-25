import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceListView: View {

    // MARK: - Properties
    @State private var viewModel: DeviceWebsocketListViewModel
    @State private var changelogViewModel: ChangelogViewModel

    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase
    @State private var selection: DeviceWithState?

    @State private var addDeviceButtonActive: Bool = false
    @State private var showSettingsSheet: Bool = false
    @State private var showSoundSyncSheet: Bool = false
    @State private var pendingShowChangelog: Bool = false
    @State private var columnVisibility = NavigationSplitViewVisibility.doubleColumn

    @AppStorage("lastSelectedDeviceMac") private var lastSelectedDeviceMac: String = ""

    private var hasHiddenDevices: Bool {
        viewModel.allDevicesWithState.contains { $0.device.isHidden }
    }

    // MARK: - init

    init(
        viewModel: DeviceWebsocketListViewModel,
        changelogViewModel: ChangelogViewModel = ChangelogViewModel()
    ) {
        _viewModel = State(wrappedValue: viewModel)
        _changelogViewModel = State(wrappedValue: changelogViewModel)
    }

    // Allow injecting a specific database (defaulting to shared for the actual app)
    init(
        database: AppDatabase = .shared,
        connectionManager: DeviceConnectionManager? = nil
    ) {
        let viewModel = DeviceWebsocketListViewModel(
            connectionManager: connectionManager,
            database: database
        )
        let changelogViewModel = ChangelogViewModel(database: database)
        self.init(viewModel: viewModel, changelogViewModel: changelogViewModel)
    }

    // MARK: - Body

    var body: some View {
        NavigationSplitView(columnVisibility: $columnVisibility) {
            list
                .toolbar { toolbar }
                .sheet(isPresented: $addDeviceButtonActive) {
                    DeviceAddView()
                }
                .sheet(
                    isPresented: $showSettingsSheet,
                    onDismiss: {
                        if pendingShowChangelog {
                            pendingShowChangelog = false
                            changelogViewModel.showAllChangelogs()
                        }
                    },
                    content: {
                        Settings(
                            showHiddenDevices: $viewModel.showHiddenDevices,
                            showOfflineDevicesLast: $viewModel.showOfflineDevicesLast,
                            theme: $viewModel.theme,
                            autoDiscovery: $viewModel.autoDiscovery,
                            onShowChangelog: {
                                pendingShowChangelog = true
                                showSettingsSheet = false
                            }
                        )
                    }
                )
                .sheet(isPresented: $showSoundSyncSheet) {
                    SoundSyncView(
                        viewModel: SoundSyncViewModel(
                            userPreferencesRepository: viewModel.userPreferencesRepository,
                            deviceRepository: viewModel.deviceRepository
                        )
                    )
                }
                .sheet(
                    isPresented: Binding(
                        get: { !changelogViewModel.changelogs.isEmpty },
                        set: { isPresented in
                            if !isPresented {
                                dismissChangelog()
                            }
                        }
                    )
                ) {
                    ChangelogSheetView(
                        changelogs: changelogViewModel.changelogs,
                        onDismiss: dismissChangelog
                    )
                }
                .navigationBarTitleDisplayMode(.inline)
        } detail: {
            detailView
        }
        .navigationSplitViewStyle(.balanced)
        .onChange(of: viewModel.theme, initial: true) { _, newTheme in
            newTheme.applyToWindows()
        }
        .task {
            async let observeTask: Void = viewModel.observe()
            async let changelogTask: Void = changelogViewModel.checkChangelog()
            _ = await (observeTask, changelogTask)
        }
        .onAppear(perform: appearAction)
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                viewModel.onResume()
            case .background:
                viewModel.onPause()
            case .inactive:
                break // Don't disconnect during transient gestures (app switcher, notifications)
            @unknown default:
                break
            }
        }
        .onChange(of: viewModel.allDevicesWithState) { _, devices in
            restoreLastSelection(from: devices)
        }
        .onChange(of: selection) { _, newSelection in
            if let mac = newSelection?.device.macAddress {
                lastSelectedDeviceMac = mac
            }
        }
        // Listen for layout changes (e.g. iPad rotation or window resizing)
        // If the user expands the window, we want to fill the empty space immediately.
        .onChange(of: horizontalSizeClass) { _, newSizeClass in
            print(
                "changed horizontalSizeClass, \(horizontalSizeClass, default: "unknown") -> \(newSizeClass, default: "unknown")"
            )
            // newSizeClass needs to be passed because the actual sizeClass is
            // not changed just yet.
            restoreLastSelection(
                from: viewModel.allDevicesWithState,
                currentSizeClass: newSizeClass
            )
        }
    }

    var list: some View {
        ZStack {
            List(selection: $selection) {
                if viewModel.localNetworkDenied {
                    Section {
                        LocalNetworkWarningView(
                            onOpenSettings: viewModel.openSettings
                        )
                        .listRowBackground(Color.clear)
                        .listRowInsets(EdgeInsets())
                        .listRowSeparator(.hidden)
                    }
                }

                if viewModel.showOfflineDevicesLast {
                    if !viewModel.onlineDevices.isEmpty {
                        deviceRows(for: viewModel.onlineDevices)
                    }

                    // Offline Devices
                    if !viewModel.offlineDevices.isEmpty {
                        Section(header: Text("Offline Devices")) {
                            deviceRows(for: viewModel.offlineDevices)
                        }
                    }
                } else if !viewModel.visibleDevices.isEmpty {
                    deviceRows(for: viewModel.visibleDevices)
                }
            }
            .listStyle(.plain)
            .refreshable(action: refreshList)
            .animation(.default, value: viewModel.localNetworkDenied)

            if viewModel.visibleDevices.isEmpty && !viewModel.localNetworkDenied {
                EmptyDeviceListView(
                    addDeviceButtonActive: $addDeviceButtonActive,
                    showHiddenDevices: $viewModel.showHiddenDevices,
                    hasHiddenDevices: hasHiddenDevices
                )
                .transition(.opacity)
            }
        }
        .animation(.default, value: viewModel.visibleDevices)
        .animation(.default, value: viewModel.onlineDevices)
        .animation(.default, value: viewModel.offlineDevices)
        .animation(.default, value: viewModel.showOfflineDevicesLast)
        .animation(.easeInOut, value: viewModel.showHiddenDevices)
        .navigationTitle("Device List")
    }
        
    @ViewBuilder
    private func deviceRows(for devices: [DeviceWithState]) -> some View {
        ForEach(devices) { device in
            DeviceListItemView(
                device: device,
                isSelected: selection == device,
                onTogglePower: { isOn in
                    viewModel.setDevicePower(for: device, isOn: isOn)
                },
                onChangeBrightness: { brightness in
                    viewModel.setBrightness(for: device, brightness: brightness)
                }
            )
            .onTapGesture {
                withAnimation {
                    selection = device
                }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
            .listRowSeparator(.hidden)
            .buttonStyle(.plain)
            .swipeActions(allowsFullSwipe: true) {
                Button(role: .destructive) {
                    withAnimation {
                        deleteItems(device: device.device)
                        let remainingDevices = devices.filter { $0 != device }
                        // Call restore in case the selected item was deleted, this
                        // will select another device (most likely the first one)
                        restoreLastSelection(from: remainingDevices)
                    }
                } label: {
                    Label("Delete", systemImage: "trash.fill")
                }
            }
        }
    }

    @ViewBuilder
    private var detailView: some View {
        if let device = selection {
            NavigationStack {
                DeviceView(device: device)
            }
        } else {
            Text("Select A Device")
                .font(.title2)
        }
    }

    // MARK: - Toolbar

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            Image(.wledLogoAkemi)
                .resizable()
                .scaledToFit()
                .padding(3)
                .frame(height: 50)
        }
        ToolbarItemGroup(placement: .primaryAction) {
            // 1. Add Button (Direct Access)
            Button {
                addDeviceButtonActive.toggle()
            } label: {
                Label("Add Device", systemImage: "plus")
            }

            // 2. Sound Sync Button
            Button {
                showSoundSyncSheet.toggle()
            } label: {
                Label("Sound Sync", systemImage: "waveform")
            }

            // 3. Settings Button
            Button {
                showSettingsSheet.toggle()
            } label: {
                Label("Settings", systemImage: "ellipsis.circle")
            }
        }
    }

    var addButton: some View {
        Button {
            addDeviceButtonActive.toggle()
        } label: {
            Label("Add New Device", systemImage: "plus")
        }
    }

    var visibilityButton: some View {
        Button {
            withAnimation {
                viewModel.showHiddenDevices.toggle()
            }
        } label: {
            if viewModel.showHiddenDevices {
                Label("Hide Hidden Devices", systemImage: "eye.slash")
            } else {
                Label("Show Hidden Devices", systemImage: "eye")
            }
        }
    }

    // MARK: - Actions

    @Sendable
    private func refreshList() async {
        viewModel.startDiscovery()
        viewModel.refreshOfflineDevices()
    }

    private func appearAction() {
        viewModel.onResume()
    }

    private func dismissChangelog() {
        Task {
            await changelogViewModel.dismiss()
        }
    }

    private func deleteItems(device: Device) {
        if selection?.device == device {
            selection = nil
        }
        viewModel.deleteDevice(device)
    }

    // MARK: - Automatic device selection

    private func restoreLastSelection(
        from devices: [DeviceWithState],
        currentSizeClass: UserInterfaceSizeClass? = nil
    ) {
        // Use the passed-in class if available, otherwise use the environment value
        let sizeClass = currentSizeClass ?? horizontalSizeClass
        // Only run if we are in a wide layout (Split View is active)
        // This prevents auto-navigation on iPhone or iPad narrow multitasking.
        guard sizeClass == .regular else { return }
        // IMPORTANT: Only try to restore if NOTHING is currently selected.
        // This prevents us from overriding a device the user just clicked on,
        // while ensuring we fill the "empty" screen if it appears.
        guard selection == nil else { return }
        // Ensure there are devices to select
        guard !devices.isEmpty else { return }

        // Try to find the last selected device by MAC address
        if let lastDevice = devices.first(where: { $0.device.macAddress == lastSelectedDeviceMac }) {
            selection = lastDevice
        }
        // Fallback: Auto-select the first device
        else if let firstDevice = devices.first {
            selection = firstDevice
        }
    }
}

#if DEBUG
#Preview {
    // Ensure some data exists in the preview context
    // swiftlint:disable redundant_discardable_let
    let _ = PreviewData.onlineDevice
    let _ = PreviewData.offlineDevice
    let _ = PreviewData.deviceWithUpdate
    let _ = PreviewData.hiddenDevice
    // swiftlint:enable redundant_discardable_let

    DeviceListView(
        database: PreviewData.database
    )
}
#endif
