import Foundation
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

@MainActor
struct WledDeviceWidgetViewSnapshotTests {

    @Test func testWidgetWideOnLightSnapshot() {
        assertWidgetSnapshot(
            data: Self.wideOnData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .light
        )
    }

    @Test func testWidgetWideOnDarkSnapshot() {
        assertWidgetSnapshot(
            data: Self.wideOnData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .dark
        )
    }

    @Test func testWidgetNarrowOffLightSnapshot() {
        assertWidgetSnapshot(
            data: Self.narrowOffData,
            layoutStyle: .narrow,
            size: CGSize(width: 155, height: 155),
            style: .light
        )
    }

    @Test func testWidgetNarrowOffDarkSnapshot() {
        assertWidgetSnapshot(
            data: Self.narrowOffData,
            layoutStyle: .narrow,
            size: CGSize(width: 155, height: 155),
            style: .dark
        )
    }

    @Test func testWidgetWideOfflineLightSnapshot() {
        assertWidgetSnapshot(
            data: Self.offlineData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .light
        )
    }

    @Test func testWidgetWideQuickActionsLightSnapshot() {
        assertWidgetSnapshot(
            data: Self.quickActionsData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .light
        )
    }

    // MARK: - Helpers

    private func assertWidgetSnapshot(
        data: WledWidgetData,
        layoutStyle: WledWidgetLayoutStyle,
        size: CGSize,
        style: UIUserInterfaceStyle,
        fileID: StaticString = #fileID,
        file: StaticString = #filePath,
        testName: String = #function,
        line: UInt = #line,
        column: UInt = #column
    ) {
        let view = WledDeviceWidgetView(
            data: data,
            layoutStyle: layoutStyle,
            elapsedTimeText: "0:00"
        )
        .frame(width: size.width, height: size.height)
        .environment(\.colorScheme, style == .dark ? .dark : .light)

        assertSnapshot(
            of: view,
            as: .image(
                drawHierarchyInKeyWindow: true,
                precision: 0.99,
                layout: .fixed(width: size.width, height: size.height),
                traits: UITraitCollection(userInterfaceStyle: style)
            ),
            fileID: fileID,
            file: file,
            testName: testName,
            line: line,
            column: column
        )
    }

    private static let wideOnData = WledWidgetData(
        macAddress: "AA:BB:CC:DD:EE:01",
        address: "192.168.1.101",
        name: "WLED Device",
        isOn: true,
        isOnline: true,
        color: Int32(bitPattern: 0xFF00BFFF),
        lastUpdated: 1_700_000_000_000
    )

    private static let narrowOffData = WledWidgetData(
        macAddress: "AA:BB:CC:DD:EE:02",
        address: "192.168.1.102",
        name: "Small widget with a long name",
        isOn: false,
        isOnline: true,
        color: Int32(bitPattern: 0xFF0000FF),
        lastUpdated: 1_700_000_000_000
    )

    private static let offlineData = WledWidgetData(
        macAddress: "AA:BB:CC:DD:EE:03",
        address: "192.168.1.103",
        name: "Offline device",
        isOn: false,
        isOnline: false,
        color: Int32(bitPattern: 0xFFFF8000),
        lastUpdated: 1_700_000_000_000
    )

    private static let quickActionsData = WledWidgetData(
        macAddress: "AA:BB:CC:DD:EE:04",
        address: "192.168.1.104",
        name: "WLED Device",
        isOn: true,
        isOnline: true,
        color: Int32(bitPattern: 0xFFDFFF00),
        lastUpdated: 1_700_000_000_000,
        quickActions: [
            WledWidgetQuickAction(id: "1", label: "a1"),
            WledWidgetQuickAction(id: "2", label: "b2"),
            WledWidgetQuickAction(id: "3", label: "c3"),
            WledWidgetQuickAction(id: "4", label: "d4")
        ]
    )
}
