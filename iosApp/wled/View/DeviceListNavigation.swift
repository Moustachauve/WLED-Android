import SwiftUI

/// Navigation state for `DeviceListView`: the sidebar selection and the detail stack path.
///
/// `NavigationSplitView` pops the detail `NavigationStack` back to its root whenever the
/// sidebar `List(selection:)` changes, by writing `[]` through the path binding during the
/// update that applies the new selection. A route set in the same transaction as the
/// selection (e.g. "edit device B" while device A is selected) would be wiped by that
/// write, so the route is re-applied once the reset has happened.
@MainActor
@Observable
final class DeviceListNavigation {
    /// Enough time for `NavigationSplitView` to perform its synchronous/next-frame detail path
    /// reset when `selection` changes, while expiring well before a user could manually tap Back.
    static let defaultResetExpiryDuration: Duration = .milliseconds(250)

    var selection: DeviceWithState?
    private(set) var detailPath: [DeviceDetailRoute] = []

    /// Route requested together with a selection change, waiting for the split view's reset.
    @ObservationIgnored private var routeAwaitingSplitViewReset: [DeviceDetailRoute]?
    @ObservationIgnored private var resetExpiryTask: Task<Void, Never>?
    @ObservationIgnored private let resetExpiryDuration: Duration

    init(
        selection: DeviceWithState? = nil,
        resetExpiryDuration: Duration = defaultResetExpiryDuration
    ) {
        self.selection = selection
        self.resetExpiryDuration = resetExpiryDuration
    }

    var detailPathBinding: Binding<[DeviceDetailRoute]> {
        Binding(
            get: { self.detailPath },
            set: { self.handleDetailPathWrite($0) }
        )
    }

    /// Shows the device's main (webview) screen.
    func select(_ device: DeviceWithState) {
        clearPendingReset()
        selection = device
        detailPath = []
    }

    /// Shows the device's edit screen, on top of its webview.
    func openEdit(_ device: DeviceWithState) {
        clearPendingReset()
        let editPath: [DeviceDetailRoute] = [.edit(deviceId: device.id)]
        if selection != nil && selection != device {
            routeAwaitingSplitViewReset = editPath
            let expiryDuration = resetExpiryDuration
            resetExpiryTask = Task { @MainActor [weak self] in
                try? await Task.sleep(for: expiryDuration)
                guard !Task.isCancelled else { return }
                self?.routeAwaitingSplitViewReset = nil
            }
        }
        selection = device
        detailPath = editPath
    }

    func clearSelection() {
        clearPendingReset()
        selection = nil
        detailPath = []
    }

    private func clearPendingReset() {
        resetExpiryTask?.cancel()
        resetExpiryTask = nil
        routeAwaitingSplitViewReset = nil
    }

    private func handleDetailPathWrite(_ newPath: [DeviceDetailRoute]) {
        detailPath = newPath
        guard newPath.isEmpty, let pendingPath = routeAwaitingSplitViewReset else { return }
        clearPendingReset()
        let deviceId = selection?.id
        // This write comes from inside the split view's update, so a task enqueued now is
        // guaranteed to run after the reset has been applied to the navigation stack.
        Task { @MainActor [weak self] in
            guard let self, self.selection?.id == deviceId, self.detailPath.isEmpty else { return }
            self.detailPath = pendingPath
        }
    }
}
