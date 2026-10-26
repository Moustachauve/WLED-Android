import Shared
import SwiftUI
import Testing
import UIKit
@testable import WLED

/// Hosts the real `DeviceListView` in a window so `NavigationSplitView` applies its
/// sidebar-selection behavior (it resets the detail stack when the selection changes).
@MainActor
struct DeviceListNavigationTests {

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

    private func makeHarness() async throws -> Harness {
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

        let navigation = DeviceListNavigation(selection: deviceA)
        let host = UIHostingController(
            rootView: DeviceListView(viewModel: viewModel, navigation: navigation)
        )
        let scene = try #require(UIApplication.shared.connectedScenes.first as? UIWindowScene)
        let window = UIWindow(windowScene: scene)
        window.rootViewController = host
        window.makeKeyAndVisible()
        try await settle()

        return Harness(
            window: window,
            navigation: navigation,
            deviceA: deviceA,
            deviceB: deviceB,
            observeTask: observeTask
        )
    }

    /// Lets SwiftUI apply the update and UIKit finish push/pop transitions.
    private func settle() async throws {
        try await Task.sleep(for: .milliseconds(1200))
    }

    /// Depth of the navigation controller hosting the device detail (sidebar excluded on iPad).
    private func detailStackDepth(_ window: UIWindow) throws -> Int {
        func navigationControllers(in controller: UIViewController) -> [UINavigationController] {
            let own = (controller as? UINavigationController).map { [$0] } ?? []
            return own + controller.children.flatMap(navigationControllers(in:))
        }
        let root = try #require(window.rootViewController)
        let deepest = try #require(navigationControllers(in: root).map(\.viewControllers.count).max())
        // On iPhone the collapsed split view stacks the device list under the detail.
        return window.traitCollection.horizontalSizeClass == .compact ? deepest - 1 : deepest
    }

    /// Polls because UIKit push/pop transitions can outlast `settle()` on a cold simulator.
    private func waitForDetailStackDepth(_ depth: Int, in window: UIWindow) async throws {
        try await waitUntil(timeout: .seconds(5)) { (try? detailStackDepth(window)) == depth }
    }

    @Test func openingEditOnAnotherDeviceWhileEditingShowsItsEditScreen() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }
        harness.navigation.openEdit(harness.deviceA)
        try await settle()

        harness.navigation.openEdit(harness.deviceB)
        try await settle()

        #expect(harness.navigation.selection == harness.deviceB)
        #expect(harness.navigation.detailPath == [.edit(deviceId: harness.deviceB.id)])
        try await waitForDetailStackDepth(2, in: harness.window)
    }

    @Test func openingEditOnAnotherDeviceFromWebviewShowsItsEditScreen() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }

        harness.navigation.openEdit(harness.deviceB)
        try await settle()

        #expect(harness.navigation.detailPath == [.edit(deviceId: harness.deviceB.id)])
        try await waitForDetailStackDepth(2, in: harness.window)
    }

    @Test func goingBackAfterSwitchingDeviceEditStaysOnWebview() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }
        harness.navigation.openEdit(harness.deviceA)
        try await settle()
        harness.navigation.openEdit(harness.deviceB)
        try await settle()

        // Simulates the back button, which pops by writing through the path binding.
        harness.navigation.detailPathBinding.wrappedValue = []
        try await settle()

        #expect(harness.navigation.detailPath.isEmpty)
        try await waitForDetailStackDepth(1, in: harness.window)
    }

    @Test func selectingAnotherDeviceWhileEditingShowsItsWebview() async throws {
        let harness = try await makeHarness()
        defer { harness.tearDown() }
        harness.navigation.openEdit(harness.deviceA)
        try await settle()

        harness.navigation.select(harness.deviceB)
        try await settle()

        #expect(harness.navigation.selection == harness.deviceB)
        #expect(harness.navigation.detailPath.isEmpty)
        try await waitForDetailStackDepth(1, in: harness.window)
    }
}
