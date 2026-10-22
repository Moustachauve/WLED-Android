import Foundation
import Shared
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct ChangelogSheetViewSnapshotTests {

    private static let sampleChangelogs = [
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
    ]

    @Test func testChangelogSheetLightSnapshot() {
        assertChangelogSheetSnapshot(style: .light)
    }

    @Test func testChangelogSheetDarkSnapshot() {
        assertChangelogSheetSnapshot(style: .dark)
    }

    private func assertChangelogSheetSnapshot(
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        let view = ChangelogSheetView(
            changelogs: Self.sampleChangelogs,
            onDismiss: {}
        )
        .environment(\.colorScheme, style == .dark ? .dark : .light)

        assertSnapshot(
            of: view,
            as: .image(
                drawHierarchyInKeyWindow: true,
                precision: 0.99,
                layout: .device(config: .iPhone13Pro),
                traits: UITraitCollection(userInterfaceStyle: style)
            ),
            fileID: fileID,
            file: file,
            testName: testName,
            line: line,
            column: column
        )
    }
}
