import AppIntents
import Shared
import SwiftUI
import WidgetKit

extension EnvironmentValues {
    /// Enables inline background rendering when hosting `WledDeviceWidgetView` outside WidgetKit
    /// (such as in snapshot tests), where `.containerBackground(for: .widget)` is inactive.
    @Entry var rendersWidgetBackgroundInView = false
}

/// Layout style for the WLED Home Screen widget based on its WidgetKit family.
enum WledWidgetLayoutStyle: Sendable {
    case narrow
    case wide

    var titleFont: Font {
        switch self {
        case .wide:
            .system(.title3, design: .rounded, weight: .bold)
        case .narrow:
            .system(.subheadline, design: .rounded, weight: .bold)
        }
    }

    var powerButtonSize: CGFloat {
        switch self {
        case .wide:
            56
        case .narrow:
            38
        }
    }

    var powerButtonIconFont: Font {
        switch self {
        case .wide:
            .title2.bold()
        case .narrow:
            .callout.weight(.semibold)
        }
    }
}

/// SwiftUI view for a WLED Home Screen widget.
struct WledDeviceWidgetView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.widgetRenderingMode) private var widgetRenderingMode
    @Environment(\.rendersWidgetBackgroundInView) private var rendersWidgetBackgroundInView

    let data: WledWidgetData?
    var layoutStyle: WledWidgetLayoutStyle = .wide
    /// Optional fixed elapsed-time string used by snapshot tests; when `nil`, renders a live timer
    /// from `data.lastUpdated`.
    var elapsedTimeText: String?

    var body: some View {
        Group {
            if let data {
                configuredContent(data: data)
            } else {
                unconfiguredContent()
            }
        }
        .background {
            if rendersWidgetBackgroundInView {
                widgetBackground()
            }
        }
        .containerBackground(for: .widget) {
            widgetBackground()
        }
    }

    private func widgetBackground() -> some View {
        ZStack {
            Color(.secondarySystemBackground)

            if let data, data.isOnline {
                Rectangle()
                    .fill(accentColor(for: data).opacity(data.isOn ? 0.20 : 0.05).gradient)
            }
        }
    }

    private func unconfiguredContent() -> some View {
        VStack {
            Image(systemName: "lightbulb.slash")
                .font(.title2.weight(.semibold))
                .foregroundStyle(.red)
                .widgetAccentable()
                .accessibilityHidden(true)
            Text("Please configure")
                .font(.system(.subheadline, design: .rounded, weight: .semibold))
                .foregroundStyle(.primary)
                .multilineTextAlignment(.center)
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func configuredContent(data: WledWidgetData) -> some View {
        Group {
            switch layoutStyle {
            case .wide:
                wideContent(data: data)
            case .narrow:
                narrowContent(data: data)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .padding()
        .widgetURL(data.deepLinkURL)
    }

    private func wideContent(data: WledWidgetData) -> some View {
        HStack(spacing: 16) {
            VStack(alignment: .leading) {
                HStack {
                    statusPill(data: data)
                    refreshTimerButton(data: data, isTrailingAligned: false)
                }

                Spacer()

                deviceDetailsColumn(data: data)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)

            powerButton(data: data)
        }
    }

    private func narrowContent(data: WledWidgetData) -> some View {
        VStack(alignment: .leading) {
            HStack {
                powerButton(data: data)
                Spacer()
                refreshTimerButton(data: data, isTrailingAligned: true)
            }

            Spacer()

            deviceDetailsColumn(data: data)
        }
    }

    private func deviceDetailsColumn(data: WledWidgetData) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(data.name)
                .font(layoutStyle.titleFont)
                .foregroundStyle(.primary)
                .lineLimit(2)
                .minimumScaleFactor(0.85)

            switch layoutStyle {
            case .wide:
                Text(data.address)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            case .narrow:
                statusContent(data: data)
                    .font(.caption.weight(.semibold))
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func statusPill(data: WledWidgetData) -> some View {
        statusContent(data: data)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(Color(.tertiarySystemFill), in: Capsule())
    }

    private func statusContent(data: WledWidgetData) -> some View {
        HStack(spacing: 4) {
            Image(
                systemName: data.signalLevel.symbolName,
                variableValue: data.signalLevel.variableValue
            )
            .symbolRenderingMode(.hierarchical)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(statusIconStyle(for: data))
            .widgetAccentable(!data.isOnline || data.isOn)
            .accessibilityHidden(true)

            if !data.isOnline {
                Text("Offline")
                    .foregroundStyle(.secondary)
            } else {
                Text(data.isOn ? "On" : "Off")
                    .foregroundStyle(data.isOn ? .primary : .secondary)
            }
        }
        .lineLimit(1)
    }

    private func statusIconStyle(for data: WledWidgetData) -> Color {
        if !data.isOnline {
            return .red
        }
        return data.isOn ? accentColor(for: data) : Color.secondary.opacity(0.5)
    }

    private func powerButton(data: WledWidgetData) -> some View {
        let size = layoutStyle.powerButtonSize
        let accent = accentColor(for: data)
        let isAccentedMode = widgetRenderingMode == .accented

        return Button(intent: SetDevicePowerIntent(device: data.deviceEntity, isOn: !data.isOn)) {
            ZStack {
                if isAccentedMode {
                    Circle()
                        .fill(Color.primary.opacity(data.isOn ? 0.22 : 0.10))
                        .overlay {
                            Circle()
                                .strokeBorder(
                                    Color.primary.opacity(data.isOn ? 0.6 : 0.25),
                                    lineWidth: data.isOn ? 2 : 1
                                )
                        }
                        .widgetAccentable(data.isOn)
                } else if data.isOn {
                    Circle()
                        .fill(accent.gradient)
                        .shadow(color: accent.opacity(0.5), radius: 8)
                } else {
                    Circle()
                        .fill(Color(.tertiarySystemFill))
                        .overlay {
                            Circle()
                                .strokeBorder(Color(.separator))
                        }
                }

                Image(systemName: "power")
                    .font(layoutStyle.powerButtonIconFont)
                    .foregroundStyle(powerIconStyle(isOn: data.isOn, isAccentedMode: isAccentedMode))
                    .widgetAccentable(data.isOn)
            }
            .frame(width: size, height: size)
            .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Toggle Power"))
        .accessibilityValue(data.isOn ? Text("On") : Text("Off"))
    }

    private func powerIconStyle(isOn: Bool, isAccentedMode: Bool) -> Color {
        if isAccentedMode {
            return isOn ? .primary : .secondary
        }
        guard isOn else { return .secondary }
        return colorScheme == .dark ? Color(.systemBackground) : .white
    }

    /// Renders a tappable refresh button paired with the elapsed timer.
    ///
    /// Note: Never apply `.fixedSize(horizontal: true)` to a view containing `Text(_, style: .timer)`.
    /// In WidgetKit's view archiver, `Text(_, style: .timer)` reports an infinite ideal width when
    /// unconstrained horizontally, which produces `NaN` layout dimensions and renders a blank widget.
    private func refreshTimerButton(data: WledWidgetData, isTrailingAligned: Bool) -> some View {
        let hasTimer = elapsedTimeText != nil || data.lastUpdated > 0
        return Button(intent: RefreshDeviceWidgetIntent(device: data.deviceEntity)) {
            HStack(spacing: 4) {
                if isTrailingAligned {
                    if hasTimer {
                        timerText(for: data)
                            .multilineTextAlignment(.trailing)
                    }
                    refreshIcon
                } else {
                    refreshIcon
                    if hasTimer {
                        timerText(for: data)
                            .multilineTextAlignment(.leading)
                    }
                }
            }
            .font(.caption2.weight(.medium))
            .monospacedDigit()
            .lineLimit(1)
            .foregroundStyle(.secondary)
            .padding(.vertical, 5)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("Refresh"))
    }

    private var refreshIcon: some View {
        Image(systemName: "arrow.clockwise")
            .font(.caption2.weight(.semibold))
    }

    @ViewBuilder
    private func timerText(for data: WledWidgetData) -> some View {
        if let elapsedTimeText {
            Text(elapsedTimeText)
        } else {
            Text(data.lastUpdatedDate, style: .timer)
        }
    }

    private func accentColor(for data: WledWidgetData) -> Color {
        let (hue, saturation) = data.hueAndSaturation
        return Color(
            hue: hue,
            saturation: max(saturation, 0.3),
            brightness: colorScheme == .dark ? 0.9 : 0.65
        )
    }
}
