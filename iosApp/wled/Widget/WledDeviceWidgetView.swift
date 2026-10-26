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
                unconfiguredContent(palette: palette)
            }
        }
        .background(palette.background)
        .containerBackground(palette.background, for: .widget)
    }

    // MARK: - Configured Content

    private func configuredContent(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                Spacer(minLength: 0)
                switch layoutStyle {
                case .wide:
                    wideContent(data: data, palette: palette)
                case .narrow:
                    narrowContent(data: data, palette: palette)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .padding(12)

            refreshButton(data: data, palette: palette)
            elapsedTimeBadge(data: data, palette: palette)
        }
        .widgetURL(data.deepLinkURL)
    }

    private func wideContent(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        HStack(alignment: .center, spacing: 8) {
            deviceDetailsColumn(
                data: data,
                palette: palette,
                alignment: .leading,
                showAddress: true
            )
            .frame(maxWidth: .infinity, alignment: .leading)

            powerButton(data: data, palette: palette)
        }
    }

    private func narrowContent(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        VStack(alignment: .center, spacing: 6) {
            powerButton(data: data, palette: palette)

            deviceDetailsColumn(
                data: data,
                palette: palette,
                alignment: .center,
                showAddress: false
            )
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }

    private func deviceDetailsColumn(
        data: WledWidgetData,
        palette: WledWidgetPalette,
        alignment: HorizontalAlignment,
        showAddress: Bool
    ) -> some View {
        VStack(alignment: alignment, spacing: 2) {
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
                    if showAddress {
                        Spacer()
                            .frame(width: 4)
                    }
                    Image(systemName: "wifi.slash")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(palette.error)
                        .accessibilityHidden(true)
                    Text("Offline")
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
        }
    }

    private func powerButton(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        Button(intent: ToggleDevicePowerIntent(device: data.deviceEntity)) {
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

                    Image(systemName: "power")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(palette.onPrimary)
                } else {
                    Image(systemName: "power")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .frame(width: 48, height: 48)
                }
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
                .padding(4)
                .frame(width: 20, height: 20)
        }
        .buttonStyle(.plain)
        .padding(8)
        .accessibilityLabel(Text("Refresh"))
    }

    @ViewBuilder
    private func elapsedTimeBadge(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        if elapsedTimeText != nil || data.lastUpdated > 0 {
            VStack {
                Spacer()
                HStack(spacing: 2) {
                    Spacer()
                    Image(systemName: "clock.arrow.circlepath")
                        .font(.system(size: 8))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .accessibilityHidden(true)
                    if let elapsedTimeText {
                        Text(elapsedTimeText)
                            .font(.system(size: 8))
                            .foregroundStyle(palette.onSurfaceVariant)
                    } else {
                        Text(
                            Date(timeIntervalSince1970: TimeInterval(data.lastUpdated) / 1000.0),
                            style: .timer
                        )
                        .font(.system(size: 8))
                        .foregroundStyle(palette.onSurfaceVariant)
                    }
                }
                .padding(.bottom, 4)
                .padding(.trailing, 20)
            }
            .allowsHitTesting(false)
        }
    }

    // MARK: - Unconfigured Content

    private func unconfiguredContent(palette: WledWidgetPalette) -> some View {
        VStack {
            Text("Please configure")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(palette.error)
                .multilineTextAlignment(.center)
                .padding(8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Dynamic tonal color palette derived from the WLED device's active LED color,
/// mirroring Android's `WidgetTheme.kt` (`createWidgetColorScheme`).
private struct WledWidgetPalette {
    let background: Color
    let surfaceContainer: Color
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
        let isOn = data?.isOn ?? false
        let (hue, rawSat) = data?.hueAndSaturation ?? (0, 0)
        let isActiveAccent = isOnline && isOn
        let saturation = isActiveAccent ? max(rawSat, 0.25) : min(rawSat, 0.12)

        if isDark {
            let bgBrightness = isActiveAccent ? 0.22 : 0.16
            self.background = Color(
                hue: hue,
                saturation: isOnline ? min(saturation * 0.55, 0.45) : 0.04,
                brightness: bgBrightness
            )
            self.surfaceContainer = Color(
                hue: hue,
                saturation: isOnline ? min(saturation * 0.65, 0.50) : 0.05,
                brightness: bgBrightness + 0.09
            )
            self.onSurface = Color(white: 0.95)
            self.onSurfaceVariant = Color(white: 0.72)
            self.primary = Color(hue: hue, saturation: max(saturation * 0.75, 0.35), brightness: 0.90)
            self.onPrimary = Color(hue: hue, saturation: min(saturation, 0.85), brightness: 0.18)
            self.glow = Color(hue: hue, saturation: min(saturation * 0.45, 0.45), brightness: 0.98)
            self.outline = Color(white: 0.50)
            self.error = Color(red: 1.0, green: 0.45, blue: 0.42)
        } else {
            let bgBrightness = isActiveAccent ? 0.95 : 0.92
            self.background = Color(
                hue: hue,
                saturation: isOnline ? min(saturation * 0.28, 0.25) : 0.02,
                brightness: bgBrightness
            )
            self.surfaceContainer = Color(
                hue: hue,
                saturation: isOnline ? min(saturation * 0.38, 0.32) : 0.03,
                brightness: 0.85
            )
            self.onSurface = Color(white: 0.12)
            self.onSurfaceVariant = Color(white: 0.35)
            self.primary = Color(hue: hue, saturation: max(saturation * 0.85, 0.55), brightness: 0.52)
            self.onPrimary = .white
            self.glow = Color(hue: hue, saturation: min(saturation * 0.30, 0.30), brightness: 0.98)
            self.outline = Color(white: 0.48)
            self.error = Color(red: 0.78, green: 0.16, blue: 0.16)
        }
    }
}
