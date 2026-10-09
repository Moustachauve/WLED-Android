//
//  DeviceInfoTwoRows.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-16.
//

import SwiftUI

struct DeviceInfoTwoRows: View {
    @Environment(\.managedObjectContext) private var viewContext
    @ObservedObject var device: DeviceWithState

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
                Text(device.device.address ?? "")
                    .lineLimit(1)
                    .fixedSize()
                    .lineSpacing(0)
                let signalStrength = Int(device.stateInfo?.info.wifi.signal ?? 0)
                Label {
                    Text(
                        device.isOnline ? "Signal Strength: \(signalStrength)" : "Offline"
                    )
                } icon: {
                    getSignalIcon(
                        isOnline: device.isOnline,
                        signalStrength: signalStrength
                    )
                }
                .labelStyle(.iconOnly)
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
        if #available(iOS 17.0, *) {
            return "arrow.down.circle.dotted"
        } else {
            return "arrow.down.circle"
        }
    }

    @ViewBuilder
    func getSignalIcon(isOnline: Bool, signalStrength: Int?) -> some View {
        let icon = !isOnline || signalStrength == nil || signalStrength == 0 ? "wifi.slash" : "wifi"

        if #available(iOS 17.0, *) {
            Image(systemName: icon, variableValue: getSignalValue(signalStrength: signalStrength))
                .symbolRenderingMode(.hierarchical)
                .font(.caption2)
            // iOS 17: Animates the signal bars filling up/down
                .contentTransition(.symbolEffect(.replace))
        } else {
            // Fallback for iOS 16
            Image(systemName: icon, variableValue: getSignalValue(signalStrength: signalStrength))
                .symbolRenderingMode(.hierarchical)
                .font(.caption2)
        }
    }

    func getSignalValue(signalStrength: Int?) -> Double {
        if let signalStrength {
            if signalStrength >= -67 {
                return 1
            }
            if signalStrength >= -80 {
                return 0.64
            }
            if signalStrength >= -100 {
                return 0.33
            }
        }
        return 0
    }
}

// MARK: - OfflineSinceText

struct OfflineSinceText: View {
    @ObservedObject var device: DeviceWithState
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

struct DeviceInfoTwoRows_Previews: PreviewProvider {

    // Let's display a device with only one bar of signal
    static var hiddenDevice: DeviceWithState = {
        let device = PreviewData.hiddenDevice
        device.stateInfo?.info.wifi.signal = -86
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
        let context = PersistenceController.preview.container.viewContext
        let device = Device(context: context)
        // Convert Date to Int64 milliseconds
        device.lastSeen = Int64(Date().addingTimeInterval(offset).timeIntervalSince1970 * 1000)
        let deviceWithState = DeviceWithState(initialDevice: device)

        return VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundStyle(.blue)
            OfflineSinceText(device: deviceWithState)
        }
    }
}
