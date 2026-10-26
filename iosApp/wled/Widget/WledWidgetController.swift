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
        fallbackAddress: String? = nil,
        fallbackIsOn: Bool = false,
        fallbackColor: Int32 = -1
    ) async -> WidgetSnapshot? {
        await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            payload: RequestPayload(
                fallbackIsOn: fallbackIsOn,
                fallbackColor: fallbackColor
            )
        )
    }

    /// Explicitly turns a device on or off and returns the updated `WidgetSnapshot`.
    func setPower(
        macAddress: String,
        isOn: Bool,
        fallbackAddress: String? = nil
    ) async -> WidgetSnapshot? {
        await sendRequest(
            macAddress: macAddress,
            fallbackAddress: fallbackAddress,
            payload: RequestPayload(
                desiredIsOn: isOn,
                fallbackIsOn: isOn
            )
        )
    }

    /// Fetches the current device state and toggles its power on or off.
    func togglePower(
        macAddress: String,
        fallbackAddress: String? = nil
    ) async -> WidgetSnapshot? {
        let current = await refreshDevice(macAddress: macAddress, fallbackAddress: fallbackAddress)
        guard let current, current.isOnline else { return current }
        return await setPower(
            macAddress: macAddress,
            isOn: !current.isOn,
            fallbackAddress: current.address
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
            payload: RequestPayload(
                desiredIsOn: shouldTurnOn,
                desiredBrightness: wledBrightness,
                fallbackIsOn: shouldTurnOn
            )
        )
    }

    // MARK: - Private Helpers

    private struct RequestPayload {
        var desiredIsOn: Bool?
        var desiredBrightness: Int32?
        var fallbackIsOn: Bool = false
        var fallbackColor: Int32 = -1
    }

    private func sendRequest(
        macAddress: String,
        fallbackAddress: String?,
        payload: RequestPayload
    ) async -> WidgetSnapshot? {
        await database.waitUntilReady()
        let device = try? await database.deviceRepository.findDeviceByMacAddress(address: macAddress)
        guard let targetAddress = device?.address ?? fallbackAddress, !targetAddress.isEmpty else {
            Self.logger.warning("No address found for device MAC \(macAddress)")
            return nil
        }

        let defaultName = String(localized: "(New Device)")
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let api = database.deviceApiFactory.create(
            address: targetAddress,
            timeout: Self.requestTimeoutSeconds
        )

        do {
            let response = try await api.postJson(
                state: JsonPost(
                    isOn: payload.desiredIsOn.map { KotlinBoolean(bool: $0) },
                    brightness: payload.desiredBrightness.map { KotlinInt(int: $0) },
                    verbose: true
                )
            )
            if response.isSuccessful, let state = response.body {
                return WidgetSnapshotKt.createWidgetSnapshotFromState(
                    device: device,
                    targetAddress: targetAddress,
                    defaultName: defaultName,
                    state: state,
                    isOnline: true,
                    fallbackIsOn: payload.desiredIsOn ?? payload.fallbackIsOn,
                    fallbackColor: payload.fallbackColor,
                    now: now
                )
            }
        } catch {
            Self.logger.error("Widget request failed for \(targetAddress): \(error.localizedDescription)")
        }

        return WidgetSnapshotKt.createWidgetSnapshotFromState(
            device: device,
            targetAddress: targetAddress,
            defaultName: defaultName,
            state: nil,
            isOnline: false,
            fallbackIsOn: payload.fallbackIsOn,
            fallbackColor: payload.fallbackColor,
            now: now
        )
    }
}
