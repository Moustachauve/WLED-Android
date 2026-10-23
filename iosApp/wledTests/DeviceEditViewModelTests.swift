import Testing
import Foundation
import Shared
@testable import WLED

@MainActor
struct DeviceEditViewModelTests {

    let database: AppDatabase
    var repository: DeviceRepository { database.deviceRepository }

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func debouncesCustomNameUpdatesAndPersistsFinalValue() async throws {
        let initial = Device(
            macAddress: "aabbcc000001",
            address: "192.168.1.10",
            originalName: "Living Room",
            customName: ""
        )
        try await repository.insert(device: initial)
        let wrapper = DeviceWithState(initialDevice: initial)
        let viewModel = DeviceEditViewModel(
            device: wrapper,
            database: database,
            customNameDebounceDuration: .milliseconds(40)
        )

        viewModel.customName = "First"
        viewModel.customName = "Final Name"

        try await waitUntil {
            try await repository.findDeviceByMacAddress(address: "aabbcc000001")?.customName == "Final Name"
        }
        #expect(wrapper.device.customName == "Final Name")
    }

    @Test func togglingHideDevicePersistsImmediately() async throws {
        let initial = Device(
            macAddress: "aabbcc000002",
            address: "192.168.1.11",
            isHidden: false,
            originalName: "Kitchen"
        )
        try await repository.insert(device: initial)
        let wrapper = DeviceWithState(initialDevice: initial)
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)

        viewModel.hideDevice = true

        try await waitUntil {
            try await repository.findDeviceByMacAddress(address: "aabbcc000002")?.isHidden == true
        }
        #expect(wrapper.device.isHidden == true)
    }

    @Test func changingBranchPersistsAndClearsSkipUpdateTag() async throws {
        let initial = Device(
            macAddress: "aabbcc000003",
            address: "192.168.1.12",
            originalName: "Desk",
            skipUpdateTag: "v0.14.0",
            branch: .stable
        )
        try await repository.insert(device: initial)
        let wrapper = DeviceWithState(initialDevice: initial)
        let viewModel = DeviceEditViewModel(device: wrapper, database: database)

        viewModel.branch = .beta

        try await waitUntil {
            let stored = try await repository.findDeviceByMacAddress(address: "aabbcc000003")
            return stored?.branch == .beta && stored?.skipUpdateTag == ""
        }
        #expect(wrapper.device.branch == .beta)
        #expect(wrapper.device.skipUpdateTag == "")
    }
}
