import AppIntents
import Foundation
import Shared
import WidgetKit

/// Errors thrown by WLED App Intents when a target device cannot be reached.
enum WledIntentError: Error, CustomLocalizedStringResourceConvertible {
    case deviceUnreachable(name: String)

    var localizedStringResource: LocalizedStringResource {
        switch self {
        case let .deviceUnreachable(name):
            "Unable to reach \(name)."
        }
    }
}

private extension WledDeviceEntity {
    func requireReachable(_ snapshot: WidgetSnapshot?) throws {
        guard let snapshot, snapshot.isOnline else {
            throw WledIntentError.deviceUnreachable(name: name)
        }
    }
}

/// Toggles a WLED device's power on or off from Widgets, Shortcuts, Siri, or Control Center.
struct ToggleDevicePowerIntent: AppIntent {
    static let title: LocalizedStringResource = "Toggle WLED Power"
    static let description = IntentDescription("Turns a WLED device on if it is off, or off if it is on.")

    @Parameter(title: "Device")
    var device: WledDeviceEntity

    init() {}

    init(device: WledDeviceEntity) {
        self.device = device
    }

    func perform() async throws -> some IntentResult {
        try device.requireReachable(
            await WledWidgetController.shared.togglePower(macAddress: device.id, fallbackAddress: device.address)
        )
        return .result()
    }
}

/// Explicitly turns a WLED device on or off from Shortcuts, Siri, or Control Center.
struct SetDevicePowerIntent: AppIntent {
    static let title: LocalizedStringResource = "Set WLED Power"
    static let description = IntentDescription("Turns a WLED device on or off.")

    @Parameter(title: "Device")
    var device: WledDeviceEntity

    @Parameter(
        title: "Power",
        default: true,
        displayName: Bool.IntentDisplayName(true: "On", false: "Off")
    )
    var isOn: Bool

    init() {}

    init(device: WledDeviceEntity, isOn: Bool) {
        self.device = device
        self.isOn = isOn
    }

    static var parameterSummary: some ParameterSummary {
        Summary("Turn \(\.$device) \(\.$isOn)")
    }

    func perform() async throws -> some IntentResult {
        try device.requireReachable(
            await WledWidgetController.shared.setPower(
                macAddress: device.id,
                isOn: isOn,
                fallbackAddress: device.address
            )
        )
        return .result()
    }
}

/// Sets a WLED device's brightness percentage (`0%` to `100%`), providing parity with Android's
/// Quick Settings `WledControlsService` range control.
struct SetDeviceBrightnessIntent: AppIntent {
    static let title: LocalizedStringResource = "Set WLED Brightness"
    static let description = IntentDescription("Sets the brightness of a WLED device from 0% to 100%.")

    @Parameter(title: "Device")
    var device: WledDeviceEntity

    @Parameter(
        title: "Brightness (%)",
        default: 100,
        inclusiveRange: (0, 100)
    )
    var brightnessPercent: Int

    init() {}

    init(device: WledDeviceEntity, brightnessPercent: Int) {
        self.device = device
        self.brightnessPercent = brightnessPercent
    }

    static var parameterSummary: some ParameterSummary {
        Summary("Set \(\.$device) brightness to \(\.$brightnessPercent)%")
    }

    func perform() async throws -> some IntentResult {
        try device.requireReachable(
            await WledWidgetController.shared.setBrightness(
                macAddress: device.id,
                percent: brightnessPercent,
                fallbackAddress: device.address
            )
        )
        return .result()
    }
}

/// Refreshes a WLED device's widget state on demand.
struct RefreshDeviceWidgetIntent: AppIntent {
    static let title: LocalizedStringResource = "Refresh WLED Device"
    static let description = IntentDescription("Refreshes the current status of a WLED device.")

    @Parameter(title: "Device")
    var device: WledDeviceEntity

    init() {}

    init(device: WledDeviceEntity) {
        self.device = device
    }

    func perform() async throws -> some IntentResult {
        try device.requireReachable(
            await WledWidgetController.shared.refreshDevice(macAddress: device.id, fallbackAddress: device.address)
        )
        return .result()
    }
}

/// Widget configuration intent allowing the user to select which WLED device a Home Screen widget controls.
struct SelectWledDeviceWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Single Device"
    static let description = IntentDescription("Control a single WLED device")

    @Parameter(title: "Device")
    var device: WledDeviceEntity?

    init() {}

    init(device: WledDeviceEntity?) {
        self.device = device
    }
}

/// Exposes default WLED shortcuts to the Shortcuts app, Siri, and iOS Control Center.
struct WledShortcutsProvider: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ToggleDevicePowerIntent(),
            phrases: [
                "Toggle \(\.$device) in \(.applicationName)",
                "Switch \(\.$device) lights in \(.applicationName)"
            ],
            shortTitle: "Toggle WLED Power",
            systemImageName: "power"
        )
        AppShortcut(
            intent: SetDevicePowerIntent(),
            phrases: [
                "Turn on \(\.$device) in \(.applicationName)"
            ],
            shortTitle: "Set WLED Power",
            systemImageName: "lightbulb.fill"
        )
        AppShortcut(
            intent: SetDeviceBrightnessIntent(),
            phrases: [
                "Set \(\.$device) brightness in \(.applicationName)"
            ],
            shortTitle: "Set WLED Brightness",
            systemImageName: "sun.max.fill"
        )
    }
}
