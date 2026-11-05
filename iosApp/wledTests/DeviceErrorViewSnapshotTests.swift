import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct DeviceErrorViewSnapshotTests {

    @Test func testDeviceErrorViewLightSnapshot() {
        assertDeviceErrorSnapshot(style: .light)
    }

    @Test func testDeviceErrorViewDarkSnapshot() {
        assertDeviceErrorSnapshot(style: .dark)
    }

    // MARK: - Helpers

    private func assertDeviceErrorSnapshot(
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        let view = DeviceErrorView(onRetry: {})
            .background(Color(UIColor.systemBackground))
            .environment(\.colorScheme, style == .dark ? .dark : .light)

        assertSnapshot(
            of: view,
            as: .image(
                drawHierarchyInKeyWindow: true,
                precision: 0.99,
                layout: .device(config: .iPhone13Pro),
                traits: UITraitCollection(userInterfaceStyle: style)
            ),
            named: "1",
            fileID: fileID,
            file: file,
            testName: testName,
            line: line,
            column: column
        )
    }
}
