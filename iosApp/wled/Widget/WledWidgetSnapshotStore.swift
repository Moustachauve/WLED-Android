import Foundation
import Shared
import WidgetKit

/// Cross-process cache for `WledWidgetData` backed by the shared App Group `UserDefaults` container.
///
/// Allows the main app (`DeviceWebsocketListViewModel`), App Intents (`WledWidgetController`), and the
/// `WLEDWidgets` extension (`WledDeviceWidgetTimelineProvider`) to share the latest widget snapshot
/// per device MAC address and trigger WidgetKit timeline reloads.
struct WledWidgetSnapshotStore: Sendable {
    static let appGroupId = "group.ca.cgagnier.wled-native"
    static let widgetKind = "ca.cgagnier.wled-native.device-widget"
    private static let keyPrefix = "wled_widget_snapshot_"

    static let shared = WledWidgetSnapshotStore()

    nonisolated(unsafe) private let userDefaults: UserDefaults?
    private let onReloadTimelines: @Sendable () -> Void

    init(
        userDefaults: UserDefaults? = UserDefaults(suiteName: Self.appGroupId) ?? .standard,
        onReloadTimelines: @escaping @Sendable () -> Void = {
            WidgetCenter.shared.reloadTimelines(ofKind: Self.widgetKind)
        }
    ) {
        self.userDefaults = userDefaults
        self.onReloadTimelines = onReloadTimelines
    }

    func load(macAddress: String) -> WledWidgetData? {
        guard !macAddress.isEmpty,
              let data = userDefaults?.data(forKey: Self.keyPrefix + macAddress) else {
            return nil
        }
        return try? JSONDecoder().decode(WledWidgetData.self, from: data)
    }

    func save(_ data: WledWidgetData, reloadTimelines: Bool = true) {
        guard !data.macAddress.isEmpty,
              let encoded = try? JSONEncoder().encode(data) else {
            return
        }
        userDefaults?.set(encoded, forKey: Self.keyPrefix + data.macAddress)
        if reloadTimelines {
            onReloadTimelines()
        }
    }

    @discardableResult
    func save(_ snapshot: WidgetSnapshot, reloadTimelines: Bool = true) -> WledWidgetData {
        let data = WledWidgetData(snapshot: snapshot)
        save(data, reloadTimelines: reloadTimelines)
        return data
    }

    func remove(macAddress: String, reloadTimelines: Bool = true) {
        guard !macAddress.isEmpty else { return }
        userDefaults?.removeObject(forKey: Self.keyPrefix + macAddress)
        if reloadTimelines {
            onReloadTimelines()
        }
    }

    /// Synchronizes live `Shared.DeviceWithState` updates into the store and reloads WidgetKit timelines
    /// only when visible widget properties (`name`, `address`, `isOn`, `isOnline`, `color`) change.
    func sync(
        devices: [Shared.DeviceWithState],
        nowMs: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        var shouldReload = false
        for device in devices {
            let mac = device.device.macAddress
            if device.isOnline, device.stateInfo != nil {
                let next = WledWidgetData(
                    snapshot: device.toWidgetSnapshot(
                        defaultName: Device.defaultDisplayName,
                        fallbackColor: -1,
                        now: nowMs
                    )
                )
                if let cached = load(macAddress: mac), cached.hasSameVisibleState(as: next) {
                    continue
                }
                save(next, reloadTimelines: false)
                shouldReload = true
            } else if let cached = load(macAddress: mac) {
                let updated = cached.updating(name: device.device.displayName, address: device.device.address)
                if !cached.hasSameVisibleState(as: updated) {
                    save(updated, reloadTimelines: false)
                    shouldReload = true
                }
            }
        }
        if shouldReload {
            onReloadTimelines()
        }
    }

    func reloadTimelines() {
        onReloadTimelines()
    }
}
