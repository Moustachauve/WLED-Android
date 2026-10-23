import Testing
import Foundation
import Shared
@testable import WLED

@MainActor
struct DeviceWebsocketListViewModelDiscoveryTests {

    let database = AppDatabase(inMemory: true)
    let discoveryService = FakeDiscoveryService()

    @Test func testLocalNetworkPermissionCallbackUpdatesDeniedState() {
        let viewModel = makeViewModel()
        #expect(viewModel.localNetworkDenied == false)

        discoveryService.onLocalNetworkPermissionChanged?(false)
        #expect(viewModel.localNetworkDenied == true)

        discoveryService.onLocalNetworkPermissionChanged?(true)
        #expect(viewModel.localNetworkDenied == false)
    }

    @Test func testStartDiscoveryAndOnPauseStopDiscovery() {
        let viewModel = makeViewModel()

        viewModel.startDiscovery()
        #expect(discoveryService.scanCallCount == 1)

        viewModel.onPause()
        #expect(discoveryService.stopCallCount == 1)
    }

    @Test func testManualDiscoveryScansWhenAutoDiscoveryDisabled() async throws {
        try await database.userPreferencesRepository.updateAutoDiscovery(autoDiscover: false)
        let viewModel = makeViewModel()

        viewModel.startDiscovery()

        #expect(discoveryService.scanCallCount == 1)
    }

    @Test func testAutoDiscoveryScansWhenEnabled() async {
        let viewModel = makeViewModel()

        await viewModel.startAutoDiscoveryIfEnabled()

        #expect(discoveryService.scanCallCount == 1)
    }

    @Test func testAutoDiscoverySkipsScanWhenDisabled() async throws {
        try await database.userPreferencesRepository.updateAutoDiscovery(autoDiscover: false)
        let viewModel = makeViewModel()

        await viewModel.startAutoDiscoveryIfEnabled()

        #expect(discoveryService.scanCallCount == 0)
    }

    @Test func testOnResumeStartsAutoDiscovery() async {
        let viewModel = makeViewModel()

        await viewModel.onResume().value

        #expect(discoveryService.scanCallCount == 1)
    }

    @Test func testOnPauseCancelsPendingAutoDiscovery() async {
        let viewModel = makeViewModel()

        let pendingDiscovery = viewModel.onResume()
        viewModel.onPause()
        await pendingDiscovery.value

        #expect(discoveryService.scanCallCount == 0)
    }

    @Test func testDisablingAutoDiscoveryCancelsPendingAutoDiscovery() async {
        let viewModel = makeViewModel()

        let pendingDiscovery = viewModel.onResume()
        viewModel.autoDiscovery = false
        await pendingDiscovery.value

        #expect(discoveryService.scanCallCount == 0)
    }

    @Test func testEnablingAutoDiscoveryStartsScan() {
        let viewModel = makeViewModel()
        viewModel.autoDiscovery = false

        viewModel.autoDiscovery = true

        #expect(discoveryService.scanCallCount == 1)
    }

    @Test func testDisablingAutoDiscoveryStopsScan() {
        let viewModel = makeViewModel()

        viewModel.autoDiscovery = false

        #expect(discoveryService.stopCallCount == 1)
    }

    @Test func testAutoDiscoveryPreferenceSyncsFromRepository() async throws {
        try await database.userPreferencesRepository.updateAutoDiscovery(autoDiscover: false)
        let viewModel = makeViewModel()
        let observationTask = Task { await viewModel.observe() }
        defer { observationTask.cancel() }

        try await waitUntil { viewModel.autoDiscovery == false }

        #expect(discoveryService.stopCallCount == 0)
    }

    // MARK: - Helpers

    private func makeViewModel() -> DeviceWebsocketListViewModel {
        DeviceWebsocketListViewModel(database: database, discoveryService: discoveryService)
    }
}
