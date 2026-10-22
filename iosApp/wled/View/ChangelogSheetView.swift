import Foundation
import MarkdownUI
import Shared
import SwiftUI

struct ChangelogSheetView: View {
    static let postScriptFontName = "PixelifySans-Bold"

    let changelogs: [VersionChangelog]
    let onDismiss: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    init(changelogs: [VersionChangelog], onDismiss: @escaping () -> Void) {
        self.changelogs = changelogs
        self.onDismiss = onDismiss
    }

    var body: some View {
        let palette = ChangelogColorPalette.forScheme(colorScheme)
        let titleText = String(localized: "What's new")
        let steppedTitle = Self.buildSteppedGradientTitle(
            text: titleText,
            stops: [palette.primary, palette.secondary, palette.tertiary]
        )

        VStack(alignment: .leading, spacing: 0) {
            Text(steppedTitle)
                .font(.custom(Self.postScriptFontName, size: 36, relativeTo: .largeTitle))
                .tracking(-0.5)
                .padding(.bottom, 10)

            ZStack(alignment: .bottomTrailing) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        ForEach(changelogs, id: \.version) { entry in
                            ChangelogVersionCardView(
                                entry: entry,
                                palette: palette
                            )
                        }
                        Spacer()
                            .frame(height: 68)
                    }
                }

                Button(action: onDismiss) {
                    Text("Awesome")
                        .fontWeight(.semibold)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 4)
                }
                .buttonStyle(.borderedProminent)
                .tint(palette.primary.swiftUIColor)
                .foregroundStyle(palette.onPrimary.swiftUIColor)
                .clipShape(Capsule())
                .shadow(color: .black.opacity(0.25), radius: 4, x: 0, y: 2)
                .padding(.trailing, 12)
                .padding(.bottom, 16)
            }
        }
        .padding(.horizontal, 20)
        .padding(.top, 20)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(palette.surface.swiftUIColor)
        .presentationDragIndicator(.visible)
        .presentationDetents([.large])
    }

    static func buildSteppedGradientTitle(
        text: String,
        stops: [ChangelogRGBColor]
    ) -> AttributedString {
        guard !text.isEmpty, !stops.isEmpty else {
            return AttributedString(text)
        }
        if stops.count == 1 {
            var attributed = AttributedString(text)
            attributed.foregroundColor = stops[0].swiftUIColor
            return attributed
        }

        let nonWhitespaceCount = text.filter { !$0.isWhitespace }.count
        var visibleIndex = 0
        var result = AttributedString()

        for char in text {
            var charString = AttributedString(String(char))
            if !char.isWhitespace {
                let fraction: Double
                if nonWhitespaceCount <= 1 {
                    fraction = 0.0
                } else {
                    fraction = Double(visibleIndex) / Double(nonWhitespaceCount - 1)
                }
                let interpolated = interpolateColorStops(stops: stops, fraction: fraction)
                charString.foregroundColor = interpolated.swiftUIColor
                visibleIndex += 1
            }
            result.append(charString)
        }

        return result
    }

    private static func interpolateColorStops(
        stops: [ChangelogRGBColor],
        fraction: Double
    ) -> ChangelogRGBColor {
        let scaled = fraction * Double(stops.count - 1)
        let segmentIndex = min(Int(scaled), stops.count - 2)
        let segmentFraction = scaled - Double(segmentIndex)
        return stops[segmentIndex].interpolated(to: stops[segmentIndex + 1], fraction: segmentFraction)
    }
}

private struct ChangelogVersionCardView: View {
    let entry: VersionChangelog
    let palette: ChangelogColorPalette

    var body: some View {
        let displayVersion = entry.isDev ? String(localized: "Dev") : entry.version

        VStack(alignment: .leading, spacing: 4) {
            Text("Version \(displayVersion)")
                .font(.custom(ChangelogSheetView.postScriptFontName, size: 24, relativeTo: .title2))
                .tracking(-0.2)
                .foregroundStyle(palette.primary.swiftUIColor)
                .padding(.bottom, 2)

            Markdown(entry.content)
                .markdownTextStyle {
                    FontSize(14)
                }
                .markdownBlockStyle(\.heading1) { configuration in
                    configuration.label
                        .markdownTextStyle {
                            FontFamily(.custom(ChangelogSheetView.postScriptFontName))
                            FontWeight(.bold)
                            FontSize(22)
                            ForegroundColor(palette.primary.swiftUIColor)
                        }
                        .markdownMargin(top: 6, bottom: 4)
                }
                .markdownBlockStyle(\.heading2) { configuration in
                    configuration.label
                        .markdownTextStyle {
                            FontWeight(.heavy)
                            FontSize(18)
                            ForegroundColor(palette.secondary.swiftUIColor)
                        }
                        .markdownMargin(top: 8, bottom: 4)
                }
                .markdownBlockStyle(\.heading3) { configuration in
                    configuration.label
                        .markdownTextStyle {
                            FontWeight(.bold)
                            FontSize(16)
                            ForegroundColor(palette.tertiary.swiftUIColor)
                        }
                        .markdownMargin(top: 8, bottom: 2)
                }
                .markdownBlockStyle(\.paragraph) { configuration in
                    configuration.label
                        .markdownMargin(top: 0, bottom: 6)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.cardSurface.swiftUIColor)
        )
    }
}

struct ChangelogRGBColor: Equatable, Sendable {
    let red: Double
    let green: Double
    let blue: Double

    var swiftUIColor: Color {
        Color(red: red, green: green, blue: blue)
    }

    func interpolated(to other: ChangelogRGBColor, fraction: Double) -> ChangelogRGBColor {
        ChangelogRGBColor(
            red: red + (other.red - red) * fraction,
            green: green + (other.green - green) * fraction,
            blue: blue + (other.blue - blue) * fraction
        )
    }
}

struct ChangelogColorPalette: Sendable {
    let primary: ChangelogRGBColor
    let onPrimary: ChangelogRGBColor
    let secondary: ChangelogRGBColor
    let tertiary: ChangelogRGBColor
    let surface: ChangelogRGBColor
    let cardSurface: ChangelogRGBColor

    static func forScheme(_ scheme: ColorScheme) -> ChangelogColorPalette {
        switch scheme {
        case .dark:
            return ChangelogColorPalette(
                primary: ChangelogRGBColor(red: 0x9A / 255.0, green: 0xCB / 255.0, blue: 0xFA / 255.0),
                onPrimary: ChangelogRGBColor(red: 0x00 / 255.0, green: 0x33 / 255.0, blue: 0x52 / 255.0),
                secondary: ChangelogRGBColor(red: 0x80 / 255.0, green: 0xD4 / 255.0, blue: 0xD6 / 255.0),
                tertiary: ChangelogRGBColor(red: 0xFF / 255.0, green: 0xB7 / 255.0, blue: 0x7D / 255.0),
                surface: ChangelogRGBColor(red: 0x18 / 255.0, green: 0x1C / 255.0, blue: 0x20 / 255.0),
                cardSurface: ChangelogRGBColor(red: 0x24 / 255.0, green: 0x28 / 255.0, blue: 0x2E / 255.0)
            )
        default:
            return ChangelogColorPalette(
                primary: ChangelogRGBColor(red: 0x2E / 255.0, green: 0x62 / 255.0, blue: 0x8C / 255.0),
                onPrimary: ChangelogRGBColor(red: 1.0, green: 1.0, blue: 1.0),
                secondary: ChangelogRGBColor(red: 0x00 / 255.0, green: 0x69 / 255.0, blue: 0x6B / 255.0),
                tertiary: ChangelogRGBColor(red: 0x88 / 255.0, green: 0x51 / 255.0, blue: 0x1E / 255.0),
                surface: ChangelogRGBColor(red: 0xF1 / 255.0, green: 0xF3 / 255.0, blue: 0xF9 / 255.0),
                cardSurface: ChangelogRGBColor(red: 0xE5 / 255.0, green: 0xE8 / 255.0, blue: 0xEE / 255.0)
            )
        }
    }
}

#if DEBUG
#Preview {
    ChangelogSheetView(
        changelogs: [
            VersionChangelog(
                version: "dev",
                content: """
                ### What's New Changelog
                Automatically see release notes in *per-version* cards after an app update, or revisit them anytime from Settings.

                ### To the Moon and Beyond!
                Added over-the-air (`OTA`) firmware update support for **QuinLED** and **MoonModules** boards.
                """,
                isDev: true
            ),
            VersionChangelog(
                version: "7.1.0",
                content: """
                ### Smoother App Switching
                Fixed an issue where the app dropped its `WebSocket` connection too quickly when opening the app switcher or moving to another app.

                ### Network Permission Alerts
                Added a helpful warning banner when **Local Network** permission is missing so you can easily find and connect to your devices.

                ### Other Improvements & Fixes
                - Faster device list filtering, sorting, and *real-time* state updates.
                - Added over-the-air (`OTA`) update support for devices running `WLED 0.16.0` and newer.
                """,
                isDev: false
            )
        ],
        onDismiss: {}
    )
}
#endif
