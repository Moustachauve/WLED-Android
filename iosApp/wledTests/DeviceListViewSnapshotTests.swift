import Foundation
import Shared
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct DeviceListViewSnapshotTests {

    let database: AppDatabase
    let repository: DeviceRepository
    let discoveryService: FakeDiscoveryService

    init() {
        self.database = AppDatabase(inMemory: true)
        self.repository = database.deviceRepository
        self.discoveryService = FakeDiscoveryService()
    }

    @Test func testDeviceListLightSnapshot() async throws {
        try await assertDeviceListSnapshot(style: .light)
    }

    @Test func testDeviceListDarkSnapshot() async throws {
        try await assertDeviceListSnapshot(style: .dark)
    }

    // MARK: - Helpers

    private func assertDeviceListSnapshot(
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) async throws {
        let viewModel = try await makePopulatedViewModel()
        let view = DeviceListView(viewModel: viewModel)

        assertSnapshot(
            of: view,
            as: .image(
                drawHierarchyInKeyWindow: true,
                precision: 0.99,
                layout: .device(config: .iPhone13Pro),
                traits: UITraitCollection(userInterfaceStyle: style)
            ),
            fileID: fileID,
            file: file,
            testName: testName,
            line: line,
            column: column
        )
    }

    private func makePopulatedViewModel() async throws -> DeviceWebsocketListViewModel {
        let onlineDevice = Device(
            macAddress: "AA:BB:CC:DD:EE:01",
            address: "192.168.1.101",
            isHidden: false,
            originalName: "Living Room WLED",
            lastSeen: 1_700_000_000_000
        )
        let offlineDevice = Device(
            macAddress: "AA:BB:CC:DD:EE:02",
            address: "192.168.1.102",
            isHidden: false,
            originalName: "Bedroom Strip",
            lastSeen: 0
        )
        let hiddenDevice = Device(
            macAddress: "AA:BB:CC:DD:EE:03",
            address: "192.168.1.103",
            isHidden: true,
            originalName: "Hidden Closet Light",
            lastSeen: 1_700_000_000_000
        )

        try await repository.insert(device: onlineDevice)
        try await repository.insert(device: offlineDevice)
        try await repository.insert(device: hiddenDevice)

        let viewModel = DeviceWebsocketListViewModel(
            database: database,
            discoveryService: discoveryService
        )

        let observationTask = Task {
            await viewModel.observe()
        }
        defer { observationTask.cancel() }

        try await waitUntil { viewModel.allDevicesWithState.count == 3 }

        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: onlineDevice,
                stateInfo: DeviceStateInfo.companion.createMock(
                    name: "Living Room WLED",
                    version: "0.15.0",
                    red: 255,
                    green: 160,
                    blue: 0,
                    brightness: 180,
                    isOn: true
                ),
                websocketStatus: .connected,
                updateVersionTag: nil
            )
        )
        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: offlineDevice,
                stateInfo: nil,
                websocketStatus: .disconnected,
                updateVersionTag: nil
            )
        )
        database.deviceConnectionManager.updateDeviceState(
            deviceWithState: Shared.DeviceWithState(
                device: hiddenDevice,
                stateInfo: DeviceStateInfo.companion.createMock(
                    name: "Hidden Closet Light",
                    version: "0.15.0",
                    red: 200,
                    green: 0,
                    blue: 255,
                    brightness: 128,
                    isOn: true
                ),
                websocketStatus: .connected,
                updateVersionTag: nil
            )
        )

        try await waitUntil {
            viewModel.onlineDevices.count == 1
                && viewModel.offlineDevices.count == 1
                && viewModel.onlineDevices.first?.stateInfo != nil
        }

        return viewModel
    }
}
