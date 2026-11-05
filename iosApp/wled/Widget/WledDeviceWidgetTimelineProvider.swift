import AppIntents
import Foundation
import Shared
import WidgetKit

/// Timeline entry representing the state of a WLED Home Screen widget at a point in time.
struct WledDeviceWidgetEntry: TimelineEntry, Equatable, Sendable {
    let date: Date
    let data: WledWidgetData?
}

/// `AppIntentTimelineProvider` for `WledDeviceWidget`, resolving device state from
/// `WledWidgetSnapshotStore` and refreshing over HTTP via `WledWidgetController`.
struct WledDeviceWidgetTimelineProvider: AppIntentTimelineProvider {
    static let refreshIntervalSeconds: TimeInterval = 15 * 60
    static let freshSnapshotWindowSeconds: TimeInterval = 5

    static let previewData = WledWidgetData(
        macAddress: "preview",
        address: "192.168.1.100",
        name: "WLED Device",
        isOn: true,
        isOnline: true,
        color: Int32(bitPattern: 0xFF00BFFF)
    )

    private let database: AppDatabase
    private let controller: WledWidgetController
    private let snapshotStore: WledWidgetSnapshotStore
    private let nowProvider: @Sendable () -> Date

    init(
        database: AppDatabase = .shared,
        controller: WledWidgetController = .shared,
        snapshotStore: WledWidgetSnapshotStore = .shared,
        nowProvider: @escaping @Sendable () -> Date = Date.init
    ) {
        self.database = database
        self.controller = controller
        self.snapshotStore = snapshotStore
        self.nowProvider = nowProvider
    }

    func placeholder(in context: Context) -> WledDeviceWidgetEntry {
        WledDeviceWidgetEntry(date: nowProvider(), data: Self.previewData)
    }

    func snapshot(
        for configuration: SelectWledDeviceWidgetIntent,
        in context: Context
    ) async -> WledDeviceWidgetEntry {
        let now = nowProvider()
        if context.isPreview {
            return WledDeviceWidgetEntry(date: now, data: Self.previewData)
        }
        let data = await resolveWidgetData(for: configuration, preferCachedOnly: true, now: now)
        return WledDeviceWidgetEntry(date: now, data: data)
    }

    func timeline(
        for configuration: SelectWledDeviceWidgetIntent,
        in context: Context
    ) async -> Timeline<WledDeviceWidgetEntry> {
        let now = nowProvider()
        let data = await resolveWidgetData(for: configuration, preferCachedOnly: false, now: now)
        let entry = WledDeviceWidgetEntry(date: now, data: data)
        return Timeline(entries: [entry], policy: .after(now.addingTimeInterval(Self.refreshIntervalSeconds)))
    }

    func resolveWidgetData(
        for configuration: SelectWledDeviceWidgetIntent,
        preferCachedOnly: Bool,
        now: Date? = nil
    ) async -> WledWidgetData? {
        let currentTime = now ?? nowProvider()
        guard let targetDevice = await resolveTargetDevice(for: configuration) else {
            return nil
        }

        if let cached = snapshotStore.load(macAddress: targetDevice.id) {
            let age = currentTime.timeIntervalSince(cached.lastUpdatedDate)
            if preferCachedOnly || (age >= 0 && age < Self.freshSnapshotWindowSeconds) {
                return cached.updating(name: targetDevice.name, address: targetDevice.address)
            }
        }

        if let snapshot = await controller.refreshDevice(
            macAddress: targetDevice.id,
            fallbackAddress: targetDevice.address,
            reloadTimelines: false
        ) {
            return WledWidgetData(snapshot: snapshot)
        }

        return snapshotStore.load(macAddress: targetDevice.id)
    }

    private func resolveTargetDevice(for configuration: SelectWledDeviceWidgetIntent) async -> WledDeviceEntity? {
        guard let configured = configuration.device else {
            return nil
        }
        await database.waitUntilReady()
        guard let stored = try? await database.deviceRepository.findDeviceByMacAddress(
            address: configured.id
        ) else {
            return nil
        }
        return WledDeviceEntity(device: stored)
    }
}
