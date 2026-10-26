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

    init(database: AppDatabase = .shared) {
        self.database = database
    }

    /// Refreshes the state of a device over HTTP and returns an updated `WidgetSnapshot`.
    func refreshDevice(
        macAddress: String,
        fallbackAddress: String? = nil
    ) async -> WidgetSnapshot? {
        await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress
        )
    }

    /// Explicitly turns a device on or off and returns the updated `WidgetSnapshot`.
    func setPower(
        macAddress: String,
        isOn: Bool,
        fallbackAddress: String? = nil,
        fallbackIsOn: Bool = false
    ) async -> WidgetSnapshot? {
        await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            desiredIsOn: isOn,
            fallbackIsOn: fallbackIsOn
        )
    }

    /// Fetches the current device state and toggles its power on or off.
    ///
    /// Note: `:shared`'s `JsonPost.isOn` is a `Boolean?` rather than WLED's `"t"` toggle literal,
    /// so we first fetch the live state (short-circuiting immediately if the device is unreachable)
    /// and then post the inverted boolean state.
    func togglePower(
        macAddress: String,
        fallbackAddress: String? = nil
    ) async -> WidgetSnapshot? {
        let current = await refreshDevice(macAddress: macAddress, fallbackAddress: fallbackAddress)
        guard let current, current.isOnline else { return current }
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
        let shouldTurnOn = wledBrightness > 0
        return await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            desiredIsOn: shouldTurnOn,
            desiredBrightness: wledBrightness
        )
    }

    // MARK: - Private Helpers

    private func sendRequest(
        macAddress: String,
        fallbackAddress: String?,
        desiredIsOn: Bool? = nil,
        desiredBrightness: Int32? = nil,
        fallbackIsOn: Bool = false
    ) async -> WidgetSnapshot? {
        await database.waitUntilReady()
        let device: Device?
        do {
            device = try await database.deviceRepository.findDeviceByMacAddress(address: macAddress)
        } catch {
            Self.logger.error("Failed to look up device MAC \(macAddress): \(error.localizedDescription)")
            device = nil
        }

        guard let targetAddress = device?.address ?? fallbackAddress, !targetAddress.isEmpty else {
            Self.logger.warning("No address found for device MAC \(macAddress)")
            return nil
        }

        let api = database.deviceApiFactory.create(
            address: targetAddress,
            timeout: Self.requestTimeoutSeconds
        )

        var state: State?
        do {
            let response = try await api.postJson(
                state: JsonPost(
                    isOn: desiredIsOn.map { KotlinBoolean(bool: $0) },
                    brightness: desiredBrightness.map { KotlinInt(int: $0) },
                    verbose: true
                )
            )
            if response.isSuccessful {
                state = response.body
            }
        } catch {
            Self.logger.error("Widget request failed for \(targetAddress): \(error.localizedDescription)")
        }

        let isOnline = state != nil
        return WidgetSnapshotKt.createWidgetSnapshotFromState(
            device: device,
            targetAddress: targetAddress,
            defaultName: Device.defaultDisplayName,
            state: state,
            isOnline: isOnline,
            macAddress: macAddress,
            fallbackIsOn: isOnline ? (desiredIsOn ?? fallbackIsOn) : fallbackIsOn,
            fallbackColor: -1,
            now: Int64(Date().timeIntervalSince1970 * 1000)
        )
    }
}
