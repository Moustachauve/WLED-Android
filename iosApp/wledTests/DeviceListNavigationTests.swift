import Shared
import SwiftUI
import Testing
import UIKit
@testable import WLED

/// Hosts the real `DeviceListView` in a window so `NavigationSplitView` applies its
/// sidebar-selection behavior (it resets the detail stack when the selection changes).
@MainActor
struct DeviceListNavigationTests {

    @MainActor
    private struct Harness {
        let window: UIWindow
        let navigation: DeviceListNavigation
        let deviceA: WLED.DeviceWithState
        let deviceB: WLED.DeviceWithState
        let observeTask: Task<Void, Never>

        func tearDown() {
            observeTask.cancel()
            window.isHidden = true
        }
    }

    private func makeHarness(selectFirstDevice: Bool = true) async throws -> Harness {
        let database = AppDatabase(inMemory: true)
        try await database.deviceRepository.insert(device: Device(
            macAddress: "AA:BB:CC:DD:EE:01",
            address: "192.168.1.101",
            originalName: "Device A"
        ))
        try await database.deviceRepository.insert(device: Device(
            macAddress: "AA:BB:CC:DD:EE:02",
            address: "192.168.1.102",
            originalName: "Device B"
        ))

        let viewModel = DeviceWebsocketListViewModel(
            database: database,
            discoveryService: FakeDiscoveryService()
        )
        let observeTask = Task { await viewModel.observe() }
        try await waitUntil { viewModel.visibleDevices.count == 2 }
        let deviceA = try #require(viewModel.visibleDevices.first { $0.id == "AA:BB:CC:DD:EE:01" })
        let deviceB = try #require(viewModel.visibleDevices.first { $0.id == "AA:BB:CC:DD:EE:02" })

        let navigation = DeviceListNavigation(selection: selectFirstDevice ? deviceA : nil)
        let host = UIHostingController(
            rootView: DeviceListView(viewModel: viewModel, navigation: navigation)
        )
        let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()

        if selectFirstDevice {
            try await waitForDetailState(
                device: deviceA,
                path: [],
                depth: 1,
                in: window,
                navigation: navigation
            )
        } else {
            try await waitUntil { window.rootViewController?.isViewLoaded == true }
        }

        return Harness(
            window: window,
            navigation: navigation,
            deviceA: deviceA,
            deviceB: deviceB,
            observeTask: observeTask
        )
    }

    private func navigationControllers(in controller: UIViewController) -> [UINavigationController] {
        let own = (controller as? UINavigationController).map { [$0] } ?? []
        return own + controller.children.flatMap(navigationControllers(in:))
    }

    /// Depth of the navigation controller hosting the device detail (sidebar excluded on iPad).
    private func detailStackDepth(_ window: UIWindow) -> Int? {
        guard let root = window.rootViewController else {
            return nil
        }
        let navs = navigationControllers(in: root)
        guard !navs.isEmpty,
              navs.allSatisfy({ $0.transitionCoordinator == nil }),
              let deepest = navs.map(\.viewControllers.count).max() else {
            return nil
        }
        // On iPhone the collapsed split view stacks the device list under the detail.
        return window.traitCollection.horizontalSizeClass == .compact ? deepest - 1 : deepest
    }

    /// Waits for `navigation` and the underlying `UINavigationController` stack to settle on the expected state.
    private func waitForDetailState(
        device: WLED.DeviceWithState,
        path: [DeviceDetailRoute],
        depth: Int,
        in window: UIWindow,
        navigation: DeviceListNavigation
    ) async throws {
        try await waitUntil(timeout: .seconds(5)) {
            guard navigation.selection == device,
                  navigation.detailPath == path,
                  detailStackDepth(window) == depth,
                  let root = window.rootViewController else {
                return false
            }
            let titles = navigationControllers(in: root)
                .flatMap(\.viewControllers)
                .compactMap(\.navigationItem.title)
            return titles.contains(device.device.displayName)
        }
    }

    private func openEditOnAAndSwitchToB(_ harness: Harness) async throws {
        harness.navigation.openEdit(harness.deviceA)
        try await waitForDetailState(
            device: harness.deviceA,
            path: [.edit(deviceId: harness.deviceA.id)],
            depth: 2,
            in: harness.window,
            navigation: harness.navigation
        )

        harness.navigation.openEdit(harness.deviceB)
        try await waitForDetailState(
            device: harness.deviceB,
            path: [.edit(deviceId: harness.deviceB.id)],
            depth: 2,
            in: harness.window,
            navigation: harness.navigation
        )
    }

    @Test func openingEditOnAnotherDeviceWhileEditingShowsItsEditScreen() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }

        try await openEditOnAAndSwitchToB(harness)

        #expect(harness.navigation.selection == harness.deviceB)
        #expect(harness.navigation.detailPath == [.edit(deviceId: harness.deviceB.id)])
        #expect(detailStackDepth(harness.window) == 2)
    }

    @Test func openingEditOnAnotherDeviceFromWebviewShowsItsEditScreen() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }

        harness.navigation.openEdit(harness.deviceB)
        try await waitForDetailState(
            device: harness.deviceB,
            path: [.edit(deviceId: harness.deviceB.id)],
            depth: 2,
            in: harness.window,
            navigation: harness.navigation
        )

        #expect(harness.navigation.detailPath == [.edit(deviceId: harness.deviceB.id)])
        #expect(detailStackDepth(harness.window) == 2)
    }

    @Test func goingBackAfterSwitchingDeviceEditStaysOnWebview() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }

        try await openEditOnAAndSwitchToB(harness)

        // Simulates the back button, which pops by writing through the path binding.
        harness.navigation.detailPathBinding.wrappedValue = []
        try await waitForDetailState(
            device: harness.deviceB,
            path: [],
            depth: 1,
            in: harness.window,
            navigation: harness.navigation
        )

        #expect(harness.navigation.detailPath.isEmpty)
        #expect(detailStackDepth(harness.window) == 1)
    }

    @Test func openingEditFromNilSelectionAndGoingBackReturnsToWebview() async throws {
        let harness = try await makeHarness(selectFirstDevice: false)
        defer { harness.tearDown() }

        harness.navigation.openEdit(harness.deviceB)
        try await waitForDetailState(
            device: harness.deviceB,
            path: [.edit(deviceId: harness.deviceB.id)],
            depth: 2,
            in: harness.window,
            navigation: harness.navigation
        )

        harness.navigation.detailPathBinding.wrappedValue = []
        try await waitForDetailState(
            device: harness.deviceB,
            path: [],
            depth: 1,
            in: harness.window,
            navigation: harness.navigation
        )

        #expect(harness.navigation.detailPath.isEmpty)
        #expect(detailStackDepth(harness.window) == 1)
    }

    @Test func selectingAnotherDeviceWhileEditingShowsItsWebview() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }

        harness.navigation.openEdit(harness.deviceA)
        try await waitForDetailState(
            device: harness.deviceA,
            path: [.edit(deviceId: harness.deviceA.id)],
            depth: 2,
            in: harness.window,
            navigation: harness.navigation
        )

        harness.navigation.select(harness.deviceB)
        try await waitForDetailState(
            device: harness.deviceB,
            path: [],
            depth: 1,
            in: harness.window,
            navigation: harness.navigation
        )

        #expect(harness.navigation.selection == harness.deviceB)
        #expect(harness.navigation.detailPath.isEmpty)
        #expect(detailStackDepth(harness.window) == 1)
    }

    @Test func expiredPendingResetDoesNotReapplyEditRouteOnBack() async throws {
        let deviceA = WLED.DeviceWithState(initialDevice: Device(
            macAddress: "AA:BB:CC:DD:EE:01",
            address: "192.168.1.101",
            originalName: "Device A"
        ))
        let deviceB = WLED.DeviceWithState(initialDevice: Device(
            macAddress: "AA:BB:CC:DD:EE:02",
            address: "192.168.1.102",
            originalName: "Device B"
        ))
        let navigation = DeviceListNavigation(
            selection: deviceA,
            resetExpiryDuration: .milliseconds(20)
        )

        navigation.openEdit(deviceB)
        try await Task.sleep(for: .milliseconds(50))

        navigation.detailPathBinding.wrappedValue = []
        await Task.yield()

        #expect(navigation.detailPath.isEmpty)
    }
}
