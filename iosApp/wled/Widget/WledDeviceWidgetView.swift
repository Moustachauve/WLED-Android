import SwiftUI
import UIKit
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
    var elapsedTimeText: String? = "0:00"
    var onTogglePower: ((Bool) -> Void)?
    var onRefresh: (() -> Void)?
    var onQuickAction: ((WledWidgetQuickAction) -> Void)?

    var body: some View {
        let palette = WledWidgetPalette(data: data, colorScheme: colorScheme)

        Group {
            if let data {
                configuredContent(data: data, palette: palette)
            } else {
                unconfiguredContent(palette: palette)
            }
        }
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
            .background(palette.background, in: RoundedRectangle(cornerRadius: 20, style: .continuous))

            refreshButton(palette: palette)

            if let elapsedTimeText {
                elapsedTimeBadge(text: elapsedTimeText, palette: palette)
            }
        }
        .widgetURL(data.deepLinkURL)
    }

    private func wideContent(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        VStack(alignment: .leading, spacing: 6) {
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

            if !data.quickActions.isEmpty {
                quickActionsRow(actions: data.quickActions, palette: palette)
            }
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

            HStack(spacing: 4) {
                if showAddress {
                    Text(data.address)
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .lineLimit(1)
                }

                if !data.isOnline {
                    if showAddress {
                        Spacer()
                            .frame(width: 4)
                    }
                    Image(systemName: "wifi.slash")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(palette.error)
                    Text("Offline")
                        .font(.system(size: 12))
                        .foregroundStyle(palette.onSurfaceVariant)
                        .lineLimit(1)
                }
            }
        }
    }

    private func powerButton(data: WledWidgetData, palette: WledWidgetPalette) -> some View {
        Button {
            onTogglePower?(!data.isOn)
        } label: {
            ZStack {
                if data.isOn {
                    Circle()
                        .fill(
                            RadialGradient(
                                colors: [palette.glow, palette.glow.opacity(0)],
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
                        .strokeBorder(palette.outlineGlow, lineWidth: 2)
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

    private func quickActionsRow(actions: [WledWidgetQuickAction], palette: WledWidgetPalette) -> some View {
        HStack(spacing: 0) {
            ForEach(Array(actions.enumerated()), id: \.element.id) { index, action in
                if index > 0 {
                    Rectangle()
                        .fill(palette.outline)
                        .frame(width: 1)
                        .padding(.vertical, 6)
                }

                Button {
                    onQuickAction?(action)
                } label: {
                    Text(action.label)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(palette.onSurface)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(height: 28)
        .background(palette.surfaceContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }

    private func refreshButton(palette: WledWidgetPalette) -> some View {
        Button {
            onRefresh?()
        } label: {
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

    private func elapsedTimeBadge(text: String, palette: WledWidgetPalette) -> some View {
        VStack {
            Spacer()
            HStack(spacing: 2) {
                Spacer()
                Image(systemName: "clock.arrow.circlepath")
                    .font(.system(size: 8))
                    .foregroundStyle(palette.onSurfaceVariant)
                Text(text)
                    .font(.system(size: 8))
                    .foregroundStyle(palette.onSurfaceVariant)
            }
            .padding(.bottom, 4)
            .padding(.trailing, 20)
        }
        .allowsHitTesting(false)
    }

    // MARK: - Unconfigured Content

    private func unconfiguredContent(palette: WledWidgetPalette) -> some View {
        VStack {
            Spacer()
            Text("Please configure")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(palette.error)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding(8)
        .background(palette.background, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }
}

// MARK: - Dynamic Widget Palette

private struct WledWidgetPalette {
    let background: Color
    let surfaceContainer: Color
    let primary: Color
    let onPrimary: Color
    let glow: Color
    let outlineGlow: Color
    let onSurface: Color
    let onSurfaceVariant: Color
    let outline: Color
    let error: Color

    init(data: WledWidgetData?, colorScheme: ColorScheme) {
        let isDark = colorScheme == .dark
        let isOnline = data?.isOnline ?? false
        let isOn = data?.isOn ?? false
        let seedUIColor = UIColor(data?.seedColor ?? .white)

        var hue: CGFloat = 0
        var saturation: CGFloat = 0
        var brightness: CGFloat = 0
        var alpha: CGFloat = 0
        seedUIColor.getHue(&hue, saturation: &saturation, brightness: &brightness, alpha: &alpha)

        let effectiveSaturation: CGFloat
        if !isOnline {
            effectiveSaturation = 0.0
        } else if !isOn {
            effectiveSaturation = min(saturation, 0.35)
        } else {
            effectiveSaturation = max(saturation, 0.65)
        }

        if isDark {
            self.background = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.45),
                brightness: isOnline && isOn ? 0.22 : 0.16
            )
            self.surfaceContainer = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.35),
                brightness: 0.28
            )
            self.primary = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.75),
                brightness: 0.88
            )
            self.onPrimary = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.8),
                brightness: 0.18
            )
            self.glow = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.3),
                brightness: 0.98
            )
            self.outlineGlow = self.glow
            self.onSurface = Color(white: 0.94)
            self.onSurfaceVariant = Color(white: 0.76)
            self.outline = Color(white: 0.58)
            self.error = Color(red: 1.0, green: 0.45, blue: 0.42)
        } else {
            self.background = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.22),
                brightness: isOnline ? 0.93 : 0.92
            )
            self.surfaceContainer = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.30),
                brightness: 0.86
            )
            self.primary = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.85),
                brightness: 0.52
            )
            self.onPrimary = .white
            self.glow = Color(
                hue: Double(hue),
                saturation: Double(effectiveSaturation * 0.25),
                brightness: 0.98
            )
            self.outlineGlow = self.glow
            self.onSurface = Color(white: 0.12)
            self.onSurfaceVariant = Color(white: 0.32)
            self.outline = Color(white: 0.48)
            self.error = Color(red: 0.75, green: 0.15, blue: 0.15)
        }
    }
}
