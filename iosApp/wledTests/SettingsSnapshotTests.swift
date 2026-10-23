import Foundation
import Shared
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct SettingsSnapshotTests {

    @Test func testSettingsLightSnapshot() {
        assertSettingsSnapshot(style: .light)
    }

    @Test func testSettingsDarkSnapshot() {
        assertSettingsSnapshot(style: .dark)
    }

    // MARK: - Helpers

    private func assertSettingsSnapshot(
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        let view = Settings(
            showHiddenDevices: .constant(false),
            showOfflineDevicesLast: .constant(true),
            theme: .constant(.auto),
            autoDiscovery: .constant(true),
            onShowChangelog: {},
            appVersion: "7.2.0",
            buildNumber: "100"
        )

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
