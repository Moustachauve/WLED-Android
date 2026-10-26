import Foundation
import SnapshotTesting
import SwiftUI
import Testing
import UIKit
@testable import WLED

struct WledDeviceWidgetViewSnapshotTests {

    struct SnapshotCase: Sendable {
        let name: String
        let data: WledWidgetData?
        let layoutStyle: WledWidgetLayoutStyle
        let size: CGSize
        let style: UIUserInterfaceStyle
    }

    static let cases: [SnapshotCase] = [
        SnapshotCase(
            name: "wideOnLight",
            data: wideOnData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .light
        ),
        SnapshotCase(
            name: "wideOnDark",
            data: wideOnData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .dark
        ),
        SnapshotCase(
            name: "narrowOffLight",
            data: narrowOffData,
            layoutStyle: .narrow,
            size: CGSize(width: 155, height: 155),
            style: .light
        ),
        SnapshotCase(
            name: "narrowOffDark",
            data: narrowOffData,
            layoutStyle: .narrow,
            size: CGSize(width: 155, height: 155),
            style: .dark
        ),
        SnapshotCase(
            name: "wideOfflineLight",
            data: offlineData,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .light
        ),
        SnapshotCase(
            name: "unconfiguredLight",
            data: nil,
            layoutStyle: .wide,
            size: CGSize(width: 329, height: 155),
            style: .light
        )
    ]

    @MainActor
    @Test(arguments: cases)
    func widgetSnapshot(_ testCase: SnapshotCase) {
        let view = WledDeviceWidgetView(
            data: testCase.data,
            layoutStyle: testCase.layoutStyle,
            elapsedTimeText: "0:00"
        )
        .frame(width: testCase.size.width, height: testCase.size.height)

        assertSnapshot(
            of: view,
            as: .image(
                perceptualPrecision: 0.98,
                layout: .fixed(width: testCase.size.width, height: testCase.size.height),
                traits: UITraitCollection(userInterfaceStyle: testCase.style)
            ),
            named: testCase.name
        )
    }

    private static let wideOnData = WledWidgetData(
        macAddress: "aabbccddeeff",
        address: "192.168.1.101",
        name: "WLED Device",
        isOn: true,
        isOnline: true,
        color: Int32(bitPattern: 0xFF00BFFF),
        lastUpdated: 1_700_000_000_000
    )

    private static let narrowOffData = WledWidgetData(
        macAddress: "aabbccddeef0",
        address: "192.168.1.102",
        name: "Small widget with a long name",
        isOn: false,
        isOnline: true,
        color: Int32(bitPattern: 0xFF0000FF),
        lastUpdated: 1_700_000_000_000
    )

    private static let offlineData = WledWidgetData(
        macAddress: "aabbccddeef1",
        address: "192.168.1.103",
        name: "Offline device",
        isOn: false,
        isOnline: false,
        color: Int32(bitPattern: 0xFFFF8000),
        lastUpdated: 1_700_000_000_000
    )
}
