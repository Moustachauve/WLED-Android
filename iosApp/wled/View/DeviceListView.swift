import SwiftUI
import Shared

private typealias State = SwiftUI.State

struct DeviceListView: View {

    // MARK: - Properties
    @State private var viewModel: DeviceWebsocketListViewModel
    @State private var changelogViewModel: ChangelogViewModel

    @Environment(\.horizontalSizeClass) var horizontalSizeClass
    @Environment(\.scenePhase) private var scenePhase
    @State private var navigation: DeviceListNavigation

    @State private var addDeviceButtonActive: Bool = false
    @State private var showSettingsSheet: Bool = false
    @State private var pendingShowChangelog: Bool = false
    @State private var devicePendingDeletion: DeviceWithState?
    @State private var columnVisibility = NavigationSplitViewVisibility.doubleColumn

    @AppStorage("lastSelectedDeviceMac") private var lastSelectedDeviceMac: String = ""

    private var hasHiddenDevices: Bool {
        viewModel.allDevicesWithState.contains { $0.device.isHidden }
    }

    // MARK: - init

    init(
        viewModel: DeviceWebsocketListViewModel,
        changelogViewModel: ChangelogViewModel = ChangelogViewModel(),
        navigation: DeviceListNavigation = DeviceListNavigation()
    ) {
        _viewModel = State(wrappedValue: viewModel)
        _changelogViewModel = State(wrappedValue: changelogViewModel)
        _navigation = State(wrappedValue: navigation)
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
        .overlay {
            if let pendingDevice = devicePendingDeletion {
                ConfirmDeleteDeviceDialog(
                    device: pendingDevice,
                    onConfirm: {
                        withAnimation {
                            confirmDelete(device: pendingDevice)
                        }
                    },
                    onCancel: {
                        withAnimation(.snappy(duration: 0.2)) {
                            devicePendingDeletion = nil
                        }
                    }
                )
                .transition(.opacity.combined(with: .scale(scale: 0.95)))
            }
        }
        .onChange(of: viewModel.theme, initial: true) { _, newTheme in
            newTheme.applyToWindows()
        }
        .task {
            async let observeTask: Void = viewModel.observe()
            async let changelogTask: Void = changelogViewModel.checkChangelog()
            _ = await (observeTask, changelogTask)
        }
        .onAppear(perform: appearAction)
        .onOpenURL { url in
            handleOpenURL(url)
        }
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
        .onChange(of: navigation.selection) { _, newSelection in
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
            List(selection: $navigation.selection) {
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
                isSelected: navigation.selection == device,
                onTogglePower: { isOn in
                    viewModel.setDevicePower(for: device, isOn: isOn)
                },
                onChangeBrightness: { brightness in
                    viewModel.setBrightness(for: device, brightness: brightness)
                }
            )
            .onTapGesture {
                withAnimation {
                    navigation.select(device)
                }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets())
            .listRowSeparator(.hidden)
            .buttonStyle(.plain)
            .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                // Intentionally omit `role: .destructive` here so UIKit does not
                // optimistically collapse and re-insert the row before the user
                // confirms deletion in `ConfirmDeleteDeviceDialog`.
                Button {
                    withAnimation(.snappy(duration: 0.2)) {
                        devicePendingDeletion = device
                    }
                } label: {
                    Label("Delete", systemImage: "trash.fill")
                }
                .tint(.red)

                Button {
                    openEditDevice(device)
                } label: {
                    Label("Edit", systemImage: "pencil")
                }
                .tint(.blue)
            }
        }
    }

    @ViewBuilder
    private var detailView: some View {
        if let device = navigation.selection {
            NavigationStack(path: navigation.detailPathBinding) {
                DeviceView(device: device)
            }
        } else {
            Text("Select A Device")
                .font(.title2)
        }
    }
}

// MARK: - Toolbar & Actions

private extension DeviceListView {
    @ToolbarContentBuilder
    var toolbar: some ToolbarContent {
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

            // 2. Settings Button
            Button {
                showSettingsSheet.toggle()
            } label: {
                Label("Settings", systemImage: "ellipsis.circle")
            }
        }
    }

    @Sendable
    func refreshList() async {
        viewModel.startDiscovery()
        viewModel.refreshOfflineDevices()
    }

    func appearAction() {
        viewModel.onResume()
    }

    func handleOpenURL(_ url: URL) {
        guard url.scheme?.lowercased() == "wled" else { return }
        // Support `wled://device/<macAddress>`, `wled://AA:BB:CC:DD:EE:FF`, `wled://AABBCCDDEEFF`, or `wled://192.168.1.50`
        let rawHost = url.host(percentEncoded: false) ?? url.host ?? ""
        let rawPath = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let rawIdentifier: String
        if rawHost.isEmpty || rawHost.caseInsensitiveCompare("device") == .orderedSame {
            rawIdentifier = rawPath
        } else {
            rawIdentifier = "\(rawHost)\(rawPath.isEmpty ? "" : ":\(rawPath)")"
        }
        let identifier = rawIdentifier.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !identifier.isEmpty else { return }

        let normalizedIdentifier = identifier.replacingOccurrences(of: ":", with: "").lowercased()
        if let matchingDevice = viewModel.allDevicesWithState.first(where: { candidate in
            let normalizedMac = candidate.device.macAddress.replacingOccurrences(of: ":", with: "").lowercased()
            return normalizedMac == normalizedIdentifier
                || candidate.device.address.caseInsensitiveCompare(identifier) == .orderedSame
        }) {
            withAnimation {
                navigation.select(matchingDevice)
            }
        }
    }

    func openEditDevice(_ device: DeviceWithState) {
        withAnimation {
            navigation.openEdit(device)
        }
    }

    func dismissChangelog() {
        Task {
            await changelogViewModel.dismiss()
        }
    }

    func confirmDelete(device: DeviceWithState) {
        devicePendingDeletion = nil
        deleteItems(device: device.device)
        let remainingDevices = viewModel.visibleDevices.filter { $0 != device }
        // Call restore in case the selected item was deleted, this
        // will select another device (most likely the first one)
        restoreLastSelection(from: remainingDevices)
    }

    func deleteItems(device: Device) {
        if navigation.selection?.device == device {
            navigation.clearSelection()
        }
        viewModel.deleteDevice(device)
    }

    // MARK: - Automatic device selection

    func restoreLastSelection(
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
        guard navigation.selection == nil else { return }
        // Ensure there are devices to select
        guard !devices.isEmpty else { return }

        // Try to find the last selected device by MAC address
        if let lastDevice = devices.first(where: { $0.device.macAddress == lastSelectedDeviceMac }) {
            navigation.select(lastDevice)
        }
        // Fallback: Auto-select the first device
        else if let firstDevice = devices.first {
            navigation.select(firstDevice)
        }
    }
}

// MARK: - Confirm Delete Dialog

struct ConfirmDeleteDeviceDialog: View {
    var device: DeviceWithState
    var onConfirm: () -> Void
    var onCancel: () -> Void

    var body: some View {
        ZStack {
            Color.black.opacity(0.4)
                .ignoresSafeArea()
                .onTapGesture(perform: onCancel)
                .accessibilityHidden(true)

            Card {
                Text("Delete Device?")
                    .font(.title3.bold())

                Card(style: .device(color: device.currentColor)) {
                    DeviceInfoTwoRows(device: device)
                }

                HStack {
                    Spacer()
                    Button("Cancel", role: .cancel, action: onCancel)
                        .buttonStyle(.bordered)
                        .keyboardShortcut(.cancelAction)
                    Button("Delete", role: .destructive, action: onConfirm)
                        .buttonStyle(.borderedProminent)
                        .tint(.red)
                }
            }
            .frame(maxWidth: 360)
            .shadow(radius: 20)
            .padding(.horizontal, 24)
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape, onCancel)
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
