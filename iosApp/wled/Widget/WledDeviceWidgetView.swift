import AppIntents
import SwiftUI
import WidgetKit

/// Layout family for the WLED widget view, matching Android's narrow (compact) and wide (row) modes.
enum WledWidgetLayoutStyle: Sendable {
    case narrow
    case wide
}

/// SwiftUI view for a WLED Home Screen widget, matching Android's `WidgetContent.kt` and `PowerButton.kt`.
struct WledDeviceWidgetView: View {
    @Environment(\.colorScheme) private var colorScheme

    let data: WledWidgetData?
    var layoutStyle: WledWidgetLayoutStyle = .wide
    /// Optional fixed elapsed-time string used by snapshot tests; when `nil`, renders a live timer
    /// from `data.lastUpdated`.
    var elapsedTimeText: String?

    var body: some View {
        let palette = WledWidgetPalette(data: data, colorScheme: colorScheme)

        Group {
            if let data {
                configuredContent(data: data, palette: palette)
            } else {
                Text("Please configure")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(palette.error)
                    .multilineTextAlignment(.center)
                    .padding(8)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        // `.containerBackground(for: .widget)` is ignored outside WidgetKit (in-app previews and snapshot tests),
        // so `.background(palette.background)` ensures the background renders in both contexts.
        .background(palette.background)
        .containerBackground(palette.background, for: .widget)
    }

    private func configuredContent(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        let isWide = layoutStyle == .wide
        let layout = isWide
            ? AnyLayout(HStackLayout(alignment: .center, spacing: 8))
            : AnyLayout(VStackLayout(alignment: .center, spacing: 6))

        return layout {
            if isWide {
                deviceDetailsColumn(data: data, palette: palette)
                powerButton(data: data, palette: palette)
            } else {
                powerButton(data: data, palette: palette)
                deviceDetailsColumn(data: data, palette: palette)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(12)
        .overlay(alignment: .topTrailing) {
            refreshButton(data: data, palette: palette)
        }
        .overlay(alignment: .bottomTrailing) {
            elapsedTimeBadge(data: data, palette: palette)
        }
        .widgetURL(data.deepLinkURL)
    }

    private func deviceDetailsColumn(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        let showAddress = layoutStyle == .wide
        return VStack(alignment: showAddress ? .leading : .center, spacing: 2) {
            Text(data.name)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(palette.onSurface)
                .lineLimit(1)
                .truncationMode(.tail)

            HStack(spacing: 4) {
                if showAddress {
                    Text(data.address)
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .lineLimit(1)
                        .truncationMode(.tail)
                }

                if !data.isOnline {
                    Image(systemName: "wifi.slash")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(palette.error)
                        .padding(.leading, showAddress ? 4 : 0)
                        .accessibilityHidden(true)
                    Text("Offline")
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: showAddress ? .leading : .center)
    }

    private func powerButton(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        Button(intent: SetDevicePowerIntent(device: data.deviceEntity, isOn: !data.isOn)) {
            ZStack {
                if data.isOn {
                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [palette.glow, palette.glow.opacity(0.0)],
                                center: .center,
                                startRadius: 4,
                                endRadius: 24
                            )
                        )
                        .frame(width: 48, height: 48)

                    Circle()
                        .fill(palette.primary)
                        .frame(width: 32, height: 32)

                    Circle()
                        .strokeBorder(palette.glow, lineWidth: 2)
                        .frame(width: 36, height: 36)
                }

                Image(systemName: "power")
                    .font(.system(size: 15, weight: data.isOn ? .bold : .semibold))
                    .foregroundStyle(data.isOn ? palette.onPrimary : palette.onSurfaceVariant)
            }
            .frame(width: 48, height: 48)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Toggle Power"))
        .accessibilityValue(data.isOn ? Text("On") : Text("Off"))
    }

    private func refreshButton(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        Button(intent: RefreshDeviceWidgetIntent(device: data.deviceEntity)) {
            Image(systemName: "arrow.clockwise")
                .font(.system(size: 11, weight: .semibold))
                .foregroundStyle(palette.outline)
                .frame(width: 20, height: 20)
                .padding(8)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Refresh"))
    }

    @ViewBuilder
    private func elapsedTimeBadge(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        if elapsedTimeText != nil || data.lastUpdated > 0 {
            let timerText = if let elapsedTimeText {
                Text(elapsedTimeText)
            } else {
                Text(data.lastUpdatedDate, style: .timer)
            }
            Text("\(Image(systemName: "clock.arrow.circlepath")) \(timerText)")
                .font(.system(size: 8))
                .monospacedDigit()
                .multilineTextAlignment(.trailing)
                .foregroundStyle(palette.onSurfaceVariant)
                .padding(.bottom, 4)
                .padding(.trailing, 20)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }
}

/// Dynamic tonal color palette derived from the WLED device's active LED color,
/// mirroring Android's `WidgetTheme.kt` (`createWidgetColorScheme`).
private struct WledWidgetPalette {
    let background: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let primary: Color
    let onPrimary: Color
    let glow: Color
    let outline: Color
    let error: Color

    init(data: WledWidgetData?, colorScheme: ColorScheme) {
        let isDark = colorScheme == .dark
        let isOnline = data?.isOnline ?? false
        let isActiveAccent = isOnline && (data?.isOn ?? false)
        let (hue, rawSat) = data?.hueAndSaturation ?? (0, 0)
        let saturation = isActiveAccent ? max(rawSat, 0.25) : min(rawSat, 0.12)

        self.background = Color(
            hue: hue,
            saturation: isOnline ? min(saturation * (isDark ? 0.55 : 0.28), isDark ? 0.45 : 0.25) : (isDark ? 0.04 : 0.02),
            brightness: isDark ? (isActiveAccent ? 0.22 : 0.16) : (isActiveAccent ? 0.95 : 0.92)
        )
        self.onSurface = Color(white: isDark ? 0.95 : 0.12)
        self.onSurfaceVariant = Color(white: isDark ? 0.72 : 0.35)
        self.primary = Color(
            hue: hue,
            saturation: max(saturation * (isDark ? 0.75 : 0.85), isDark ? 0.35 : 0.55),
            brightness: isDark ? 0.90 : 0.52
        )
        self.onPrimary = isDark ? Color(hue: hue, saturation: min(saturation, 0.85), brightness: 0.18) : .white
        self.glow = Color(hue: hue, saturation: min(saturation * (isDark ? 0.45 : 0.30), isDark ? 0.45 : 0.30), brightness: 0.98)
        self.outline = Color(white: isDark ? 0.50 : 0.48)
        self.error = isDark ? Color(red: 1.0, green: 0.45, blue: 0.42) : Color(red: 0.78, green: 0.16, blue: 0.16)
    }
}
