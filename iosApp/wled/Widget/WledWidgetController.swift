import Foundation
import OSLog
import Shared

/// Controller responsible for fetching and mutating WLED device state for iOS widgets and App Intents.
///
/// Mirrors Android's `WledWidgetManager` and `WledControlsService` using `:shared`'s `DeviceRepository`,
/// `DeviceApiFactory`, `WidgetSnapshot`, and `BrightnessUtils`.
struct WledWidgetController: Sendable {
    static let shared = WledWidgetController()

    /// Matches `WledWidgetManager.WIDGET_REQUEST_TIMEOUT_SECONDS` on Android.
    static let requestTimeoutSeconds: Int64 = 5

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "WledWidgetController"
    )

    private let database: AppDatabase
    private let snapshotStore: WledWidgetSnapshotStore

    init(
        database: AppDatabase = .shared,
        snapshotStore: WledWidgetSnapshotStore = .shared
    ) {
        self.database = database
        self.snapshotStore = snapshotStore
    }

    /// Refreshes the state of a device over HTTP and returns an updated `WidgetSnapshot`.
    func refreshDevice(
        macAddress: String,
        fallbackAddress: String? = nil,
        reloadTimelines: Bool = true
    ) async -> WidgetSnapshot? {
        await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            reloadTimelines: reloadTimelines
        )
    }

    /// Explicitly turns a device on or off and returns the updated `WidgetSnapshot`.
    func setPower(
        macAddress: String,
        isOn: Bool,
        fallbackAddress: String? = nil,
        fallbackIsOn: Bool? = nil
    ) async -> WidgetSnapshot? {
        await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            desiredIsOn: isOn,
            fallbackIsOn: fallbackIsOn
        )
    }

    /// Fetches the current device state and toggles its power on or off.
    func togglePower(macAddress: String, fallbackAddress: String? = nil) async -> WidgetSnapshot? {
        let current = await refreshDevice(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            reloadTimelines: false
        )
        guard let current, current.isOnline else {
            if current != nil {
                snapshotStore.reloadTimelines()
            }
            return current
        }
        return await setPower(
            macAddress: macAddress,
            isOn: !current.isOn,
            fallbackAddress: current.address,
            fallbackIsOn: current.isOn
        )
    }

    /// Sets a device's brightness from a percentage (`0...100`), converting to WLED's `0...255` range
    /// via `:shared`'s `BrightnessUtilsKt.percentToBrightness`.
    func setBrightness(
        macAddress: String,
        percent: Int,
        fallbackAddress: String? = nil
    ) async -> WidgetSnapshot? {
        let wledBrightness = BrightnessUtilsKt.percentToBrightness(percent: Float(percent))
        return await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            desiredIsOn: wledBrightness > 0,
            desiredBrightness: wledBrightness
        )
    }

    private func sendRequest(
        macAddress: String,
        fallbackAddress: String?,
        desiredIsOn: Bool? = nil,
        desiredBrightness: Int32? = nil,
        fallbackIsOn: Bool? = nil,
        reloadTimelines: Bool = true
    ) async -> WidgetSnapshot? {
        await database.waitUntilReady()
        let device = try? await database.deviceRepository.findDeviceByMacAddress(address: macAddress)
        let cached = snapshotStore.load(macAddress: macAddress)

        guard let targetAddress = device?.address ?? fallbackAddress ?? cached?.address,
              !targetAddress.isEmpty else {
            Self.logger.warning("No address found for device MAC \(macAddress)")
            return nil
        }

        let api = database.deviceApiFactory.create(
            address: targetAddress,
            timeout: Self.requestTimeoutSeconds
        )
        let response = try? await api.postJson(
            state: JsonPost(
                isOn: desiredIsOn.map(KotlinBoolean.init(bool:)),
                brightness: desiredBrightness.map(KotlinInt.init(int:)),
                verbose: true
            )
        )
        let state = (response?.isSuccessful == true) ? response?.body : nil
        let isOnline = state != nil
        let effectiveFallbackIsOn = fallbackIsOn ?? cached?.isOn ?? false

        let snapshot = WidgetSnapshotKt.createWidgetSnapshotFromState(
            device: device,
            targetAddress: targetAddress,
            defaultName: Device.defaultDisplayName,
            state: state,
            isOnline: isOnline,
            macAddress: macAddress,
            fallbackIsOn: isOnline ? (desiredIsOn ?? effectiveFallbackIsOn) : effectiveFallbackIsOn,
            fallbackColor: cached?.color ?? -1,
            fallbackSignalLevel: cached?.signalLevel ?? .good,
            now: Int64(Date().timeIntervalSince1970 * 1000)
        )
        snapshotStore.save(snapshot, reloadTimelines: reloadTimelines)
        return snapshot
    }
}
