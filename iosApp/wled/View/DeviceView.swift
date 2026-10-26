import SwiftUI
import Shared

private typealias State = SwiftUI.State

/// Screens that can be pushed on top of a device's webview in the detail `NavigationStack`.
/// Routes carry the device id so that switching devices replaces the pushed screen.
enum DeviceDetailRoute: Hashable {
    case edit(deviceId: String)
}

struct DeviceView: View {
    @Environment(\.colorScheme) var colorScheme
    var device: DeviceWithState

    @State var showDownloadFinished = false
    @State var shouldWebViewRefresh = false

    var body: some View {
        ZStack {
            WebView(url: getDeviceAddress(), reload: $shouldWebViewRefresh) { _ in
                withAnimation {
                    showDownloadFinished = true
                }
                Task {
                    try await Task.sleep(for: .seconds(3))
                    withAnimation {
                        showDownloadFinished = false
                    }
                }
            }
            if showDownloadFinished {
                VStack {
                    Spacer()
                    Text("Download Completed")
                        .font(.title3)
                        .padding()
                        .background(.regularMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 15))
                        .padding(.bottom)
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(1) // Ensures it stays on top during transition
            }
        }
        .navigationTitle(device.device.displayName)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(for: DeviceDetailRoute.self) { route in
            switch route {
            case .edit:
                DeviceEditView(device: device)
                    .id(device.id)
            }
        }
        .toolbar { toolbar }
    }

    @ToolbarContentBuilder
    var toolbar: some ToolbarContent {
        ToolbarItem(placement: .principal) {
            DeviceInfoTwoRows(device: device)
        }
        ToolbarItem(placement: .primaryAction) {
            NavigationLink(value: DeviceDetailRoute.edit(deviceId: device.id)) {
                Label("Settings", systemImage: "gear")
                // This badge only works on iOS 26+, but that's fine.
                    .badge(getToolbarBadgeCount())
            }
        }
        ToolbarItem(placement: .automatic) {
            Button("Refresh", systemImage: "arrow.clockwise") {
                shouldWebViewRefresh = true
            }
        }
    }

    func getDeviceAddress() -> URL? {
        let deviceAddress = device.device.address
        guard let url = URL(string: "http://\(deviceAddress)") else {
            return nil
        }
        return url
    }

    func getToolbarBadgeCount() -> Int {
        return device.hasUpdateAvailable ? 1 : 0
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceView(device: PreviewData.onlineDevice)
    }
}
#endif
