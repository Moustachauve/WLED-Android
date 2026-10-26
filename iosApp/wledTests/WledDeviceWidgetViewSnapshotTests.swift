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
        let style: UIUserInterfaceStyle

        var size: CGSize {
            switch layoutStyle {
            case .wide:
                CGSize(width: 329, height: 155)
            case .narrow:
                CGSize(width: 155, height: 155)
            }
        }

        init(
            _ name: String,
            data: WledWidgetData?,
            layoutStyle: WledWidgetLayoutStyle = .wide,
            style: UIUserInterfaceStyle = .light
        ) {
            self.name = name
            self.data = data
            self.layoutStyle = layoutStyle
            self.style = style
        }
    }

    static let cases: [SnapshotCase] = [
        SnapshotCase("wideOnLight", data: wideOnData, layoutStyle: .wide, style: .light),
        SnapshotCase("wideOnDark", data: wideOnData, layoutStyle: .wide, style: .dark),
        SnapshotCase("narrowOffLight", data: narrowOffData, layoutStyle: .narrow, style: .light),
        SnapshotCase("narrowOffDark", data: narrowOffData, layoutStyle: .narrow, style: .dark),
        SnapshotCase("wideOfflineLight", data: offlineData, layoutStyle: .wide, style: .light),
        SnapshotCase("unconfiguredLight", data: nil, layoutStyle: .wide, style: .light)
    ]

    @MainActor
    @Test(arguments: cases)
    func widgetSnapshot(_ testCase: SnapshotCase) {
        let view = WledDeviceWidgetView(
            data: testCase.data,
            layoutStyle: testCase.layoutStyle,
            elapsedTimeText: "0:00"
        )

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

    private static let wideOnData = makeData(
        name: "WLED Device",
        isOn: true,
        isOnline: true,
        color: 0xFF00BFFF
    )

    private static let narrowOffData = makeData(
        name: "Small widget with a long name",
        isOn: false,
        isOnline: true,
        color: 0xFF0000FF
    )

    private static let offlineData = makeData(
        name: "Offline device",
        isOn: false,
        isOnline: false,
        color: 0xFFFF8000
    )

    private static func makeData(
        name: String,
        isOn: Bool,
        isOnline: Bool,
        color: UInt32
    ) -> WledWidgetData {
        WledWidgetData(
            macAddress: "aabbccddeeff",
            address: "192.168.1.101",
            name: name,
            isOn: isOn,
            isOnline: isOnline,
            color: Int32(bitPattern: color),
            lastUpdated: 1_700_000_000_000
        )
    }
}
