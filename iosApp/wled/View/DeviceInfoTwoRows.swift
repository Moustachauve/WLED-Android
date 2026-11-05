//
//  DeviceInfoTwoRows.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-16.
//

import SwiftUI
import Shared

struct DeviceInfoTwoRows: View {
    var device: DeviceWithState

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 4) {
                Text(device.device.displayName)
                    .font(.headline.leading(.tight))
                    .lineLimit(2)
                if device.hasUpdateAvailable {
                    Label("Update available", systemImage: getUpdateIconName())
                        .labelStyle(.iconOnly)
                        .font(.subheadline.leading(.tight))
                }
            }
            HStack(spacing: 4) {
                WebsocketStatusIndicator(currentStatus: device.websocketStatus)
                Text(device.device.address)
                    .lineLimit(1)
                    .fixedSize()
                    .lineSpacing(0)
                let signalRssi = device.stateInfo?.info.wifi.rssi
                let signalLevel = WifiSignalLevel.companion.fromRssi(
                    rssi: signalRssi,
                    isOnline: device.isOnline,
                    defaultWhenNull: .offline
                )
                Label {
                    Text(
                        device.isOnline ? "Signal Strength: \(signalRssi?.intValue ?? 0)" : "Offline"
                    )
                } icon: {
                    getSignalIcon(signalLevel: signalLevel)
                }
                .labelStyle(.iconOnly)
                if let batteryPercentage = device.stateInfo?.info.userMods?.batteryPercentage?.doubleValue {
                    let roundedBattery = Int(batteryPercentage.rounded())
                    Label {
                        Text("Battery: \(roundedBattery)%")
                    } icon: {
                        Image(systemName: Self.batteryIconName(for: batteryPercentage))
                            .font(.caption2)
                    }
                    .labelStyle(.iconOnly)
                }
                if !device.isOnline {
                    OfflineSinceText(device: device)
                        .lineLimit(1)
                        .allowsTightening(true)
                        .foregroundStyle(.secondary)
                        .lineSpacing(0)
                        .minimumScaleFactor(0.6)
                }
                if device.device.isHidden {
                    HStack(spacing: 3) {
                        Image(systemName: "eye.slash")
                            .accessibilityHidden(true)
                        Text("(Hidden)")
                    }
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel("(Hidden)")
                }
            }
            .font(.subheadline.leading(.tight))

        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    func getUpdateIconName() -> String {
        "arrow.down.circle.dotted"
    }

    @ViewBuilder
    func getSignalIcon(signalLevel: WifiSignalLevel) -> some View {
        Image(systemName: signalLevel.symbolName, variableValue: signalLevel.variableValue)
            .symbolRenderingMode(.hierarchical)
            .font(.caption2)
            .contentTransition(.symbolEffect(.replace))
    }

    static func batteryIconName(for batteryPercentage: Double) -> String {
        if batteryPercentage <= 10 {
            return "battery.0percent"
        }
        if batteryPercentage <= 35 {
            return "battery.25percent"
        }
        if batteryPercentage <= 60 {
            return "battery.50percent"
        }
        if batteryPercentage <= 85 {
            return "battery.75percent"
        }
        return "battery.100percent"
    }
}

// MARK: - OfflineSinceText

struct OfflineSinceText: View {
    var device: DeviceWithState
    @Environment(\.locale) private var locale

    private let formatter: RelativeDateTimeFormatter = {
        let fmt = RelativeDateTimeFormatter()
        fmt.unitsStyle = .full // Generates "10 minutes ago", "1 hour ago"
        fmt.dateTimeStyle = .named // Allows "yesterday" instead of "1 day ago" if appropriate
        return fmt
    }()

    var body: some View {
        // Update the view every minute to keep the "ago" text fresh
        TimelineView(.periodic(from: .now, by: 60)) { context in
            getOfflineText(now: context.date)
        }
    }

    private func getOfflineText(now: Date) -> Text {
        // lastSeen is Int64 milliseconds. 0 usually means never seen/unknown.
        let lastSeenMs = device.device.lastSeen

        guard lastSeenMs > 0 else {
            return Text("(Offline)")
        }

        formatter.locale = locale
        let lastSeenDate = Date(timeIntervalSince1970: TimeInterval(lastSeenMs) / 1000)
        let diff = now.timeIntervalSince(lastSeenDate)

        // Handle the "less than a minute" case manually
        if diff < 60 {
            return Text("(Offline, less than a minute ago)")
        }

        // For everything else (minutes, hours, days), let Apple handle the linguistics
        let timeString = formatter.localizedString(for: lastSeenDate, relativeTo: now)

        // formatter returns "10 minutes ago", so we prepend "Offline, "
        // Using string interpolation here works because the formatter output is already localized/pluralized
        return Text("(Offline, \(timeString))")
    }
}

// MARK: - Previews

// MARK: DeviceInfoTwoRows preview

#if DEBUG
struct DeviceInfoTwoRows_Previews: PreviewProvider {

    // Let's display a device with only one bar of signal
    static var hiddenDevice: DeviceWithState = {
        let device = PreviewData.hiddenDevice
        if let current = device.stateInfo {
            device.stateInfo = current.withSignal(signal: -86)
        }
        return device
    }()

    static var previews: some View {
        VStack(spacing: 20) {
            DeviceInfoTwoRows(device: PreviewData.onlineDevice)
            DeviceInfoTwoRows(device: PreviewData.offlineDevice)
            DeviceInfoTwoRows(device: PreviewData.deviceWithUpdate)
            DeviceInfoTwoRows(device: DeviceInfoTwoRows_Previews.hiddenDevice)
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
#endif

// MARK: OfflineSinceText preview

struct OfflineSinceText_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            // English (Default)
            previewList
                .previewDisplayName("Offline Since (English)")

            // French (Explicit)
            previewList
                .environment(\.locale, Locale(identifier: "fr-CA"))
                .previewDisplayName("Offline Since (French)")
        }
        .previewLayout(.sizeThatFits)
    }

    static var previewList: some View {
        VStack(alignment: .leading, spacing: 20) {
            createPreview(offset: -30, label: "Less than a minute")
            createPreview(offset: -15 * 60, label: "15 minutes ago")
            createPreview(offset: -25 * 60 * 60, label: "Yesterday")
            createPreview(offset: -61 * 24 * 60 * 60, label: "2 months ago")
        }
        .padding()
    }

    // Helper to create the device and view
    static func createPreview(offset: TimeInterval, label: String) -> some View {
        let lastSeen = Int64(Date().addingTimeInterval(offset).timeIntervalSince1970 * 1000)
        let device = Device(
            macAddress: "mock:mac:\(offset)",
            address: "192.168.1.100",
            lastSeen: lastSeen
        )
        let deviceWithState = DeviceWithState(initialDevice: device)

        return VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.blue)
            OfflineSinceText(device: deviceWithState)
        }
    }
}
