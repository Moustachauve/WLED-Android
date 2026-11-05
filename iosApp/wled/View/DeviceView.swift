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
    @State private var isWebViewError = false
    @State private var isWebViewLoading = false

    var body: some View {
        ZStack {
            WebView(
                url: getDeviceAddress(),
                reload: $shouldWebViewRefresh,
                isError: $isWebViewError,
                isLoading: $isWebViewLoading
            ) { _ in
                withAnimation {
                    showDownloadFinished = true
                }
                Task {
                    try? await Task.sleep(for: .seconds(3))
                    guard !Task.isCancelled else { return }
                    withAnimation {
                        showDownloadFinished = false
                    }
                }
            }
            .opacity(isWebViewError ? 0 : 1)
            .accessibilityHidden(isWebViewError)
            .overlay(alignment: .top) {
                if isWebViewLoading && !isWebViewError {
                    IndeterminateProgressBar()
                }
            }

            if isWebViewError {
                DeviceErrorView(onRetry: refreshWebView)
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
        .onChange(of: device.device.address) {
            isWebViewError = false
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
            Button("Refresh", systemImage: "arrow.clockwise", action: refreshWebView)
        }
    }

    private func refreshWebView() {
        shouldWebViewRefresh = true
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

private struct IndeterminateProgressBar: View {
    @State private var progress: CGFloat = 0

    var body: some View {
        GeometryReader { geometry in
            let barWidth = geometry.size.width * 0.35
            ZStack(alignment: .leading) {
                Rectangle()
                    .fill(.tint.opacity(0.2))
                Capsule()
                    .fill(.tint)
                    .frame(width: barWidth)
                    .offset(x: (geometry.size.width + barWidth) * progress - barWidth)
            }
        }
        .frame(height: 3)
        .clipped()
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: false)) {
                progress = 1
            }
        }
        .accessibilityHidden(true)
    }
}

#if DEBUG
#Preview {
    NavigationStack {
        DeviceView(device: PreviewData.onlineDevice)
    }
}
#endif
