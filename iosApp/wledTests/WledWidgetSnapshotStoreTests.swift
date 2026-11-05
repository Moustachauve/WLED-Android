import Foundation
import os
import Shared
import Testing
@testable import WLED

struct WledWidgetSnapshotStoreTests {
    let reloads = OSAllocatedUnfairLock(initialState: 0)
    let store: WledWidgetSnapshotStore

    init() {
        let reloads = self.reloads
        let defaults = UserDefaults(suiteName: "snapshot-store-test-\(UUID().uuidString)")
        self.store = WledWidgetSnapshotStore(
            userDefaults: defaults,
            onReloadTimelines: { reloads.withLock { $0 += 1 } }
        )
    }

    private func sampleData(mac: String = "aabbccddeeff") -> WledWidgetData {
        WledWidgetData(
            macAddress: mac,
            address: "192.168.1.50",
            name: "Living Room",
            isOn: true,
            isOnline: true,
            color: Int32(bitPattern: 0xFF00BFFF),
            lastUpdated: 1_700_000_000_000
        )
    }

    private func makeDeviceWithState(
        mac: String = "aabbccddeeff",
        address: String = "192.168.1.50",
        customName: String = "Desk Strip",
        isOnline: Bool = true,
        isOn: Bool = true
    ) -> Shared.DeviceWithState {
        let device = Device(
            macAddress: mac,
            address: address,
            originalName: "WLED",
            customName: customName
        )
        let stateInfo = isOnline
            ? DeviceStateInfo.companion.createMock(
                name: "WLED",
                version: "0.15.0",
                red: 255,
                green: 0,
                blue: 0,
                brightness: 128,
                isOn: isOn
            )
            : nil
        return Shared.DeviceWithState(
            device: device,
            stateInfo: stateInfo,
            websocketStatus: isOnline ? .connected : .disconnected,
            updateVersionTag: nil
        )
    }

    @Test func saveAndLoadRoundTripsWidgetData() {
        let expected = sampleData()

        store.save(expected)
        let loaded = store.load(macAddress: expected.macAddress)

        #expect(loaded == expected)
        #expect(reloads.withLock { $0 } == 1)
    }

    @Test func saveWithReloadTimelinesFalseSkipsTimelineReload() {
        let expected = sampleData()

        store.save(expected, reloadTimelines: false)

        #expect(store.load(macAddress: expected.macAddress) == expected)
        #expect(reloads.withLock { $0 } == 0)
    }

    @Test func removeDeletesCachedSnapshotAndReloadsTimelines() {
        let expected = sampleData()
        store.save(expected, reloadTimelines: false)

        store.remove(macAddress: expected.macAddress)

        #expect(store.load(macAddress: expected.macAddress) == nil)
        #expect(reloads.withLock { $0 } == 1)
    }

    @Test func syncSavesOnlineDeviceAndSkipsReloadWhenUnchanged() {
        let online = makeDeviceWithState(isOn: true)

        store.sync(devices: [online], nowMs: 1_000)
        #expect(store.load(macAddress: "aabbccddeeff")?.isOn == true)
        #expect(reloads.withLock { $0 } == 1)

        store.sync(devices: [online], nowMs: 2_000)
        #expect(reloads.withLock { $0 } == 1)
    }

    @Test func syncReloadsWhenVisibleStateChanges() {
        store.sync(devices: [makeDeviceWithState(isOn: true)], nowMs: 1_000)
        reloads.withLock { $0 = 0 }

        store.sync(devices: [makeDeviceWithState(isOn: false)], nowMs: 2_000)

        #expect(store.load(macAddress: "aabbccddeeff")?.isOn == false)
        #expect(reloads.withLock { $0 } == 1)
    }

    @Test func syncUpdatesCachedMetadataWhenDeviceIsOffline() {
        store.sync(devices: [makeDeviceWithState(customName: "Desk Strip", isOn: true)], nowMs: 1_000)
        reloads.withLock { $0 = 0 }

        store.sync(devices: [makeDeviceWithState(customName: "Renamed Strip", isOnline: false)], nowMs: 2_000)

        let cached = store.load(macAddress: "aabbccddeeff")
        #expect(cached?.name == "Renamed Strip")
        #expect(cached?.isOnline == true)
        #expect(cached?.isOn == true)
        #expect(reloads.withLock { $0 } == 1)
    }

    @Test func syncReloadsWhenSignalLevelBucketChanges() {
        let strongDevice = makeDeviceWithState(isOn: true)
        store.sync(devices: [strongDevice], nowMs: 1_000)
        reloads.withLock { $0 = 0 }

        let weakDevice = makeDeviceWithState(isOn: true)
        let weakStateInfo = weakDevice.stateInfo?.withSignal(signal: -85)
        let updatedDevice = Shared.DeviceWithState(
            device: weakDevice.device,
            stateInfo: weakStateInfo,
            websocketStatus: .connected,
            updateVersionTag: nil
        )

        store.sync(devices: [updatedDevice], nowMs: 2_000)

        #expect(store.load(macAddress: "aabbccddeeff")?.signalLevel == .weak)
        #expect(reloads.withLock { $0 } == 1)
    }
}
