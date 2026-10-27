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
        SnapshotCase("wideOnLight", data: wideOnData),
        SnapshotCase("wideOnDark", data: wideOnData, style: .dark),
        SnapshotCase("narrowOffLight", data: narrowOffData, layoutStyle: .narrow),
        SnapshotCase("narrowOffDark", data: narrowOffData, layoutStyle: .narrow, style: .dark),
        SnapshotCase("wideOfflineLight", data: offlineData),
        SnapshotCase("unconfiguredLight", data: nil)
    ]

    static let liveTimerCases: [SnapshotCase] = [
        SnapshotCase("wideOnLight", data: wideOnData),
        SnapshotCase("narrowOffLight", data: narrowOffData, layoutStyle: .narrow)
    ]

    @MainActor
    @Test(arguments: cases)
    func widgetSnapshot(_ testCase: SnapshotCase) {
        let view = WledDeviceWidgetView(
            data: testCase.data,
            layoutStyle: testCase.layoutStyle,
            elapsedTimeText: "0:00"
        )
        .environment(\.rendersWidgetBackgroundInView, true)

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

    /// Exercises the production `WledDeviceWidgetView` initializer (with `elapsedTimeText == nil`,
    /// which renders `Text(_, style: .timer)`) and verifies it matches the reference snapshot.
    @MainActor
    @Test(arguments: liveTimerCases)
    func productionLiveTimerMatchesSnapshot(_ testCase: SnapshotCase) throws {
        let baseData = try #require(testCase.data)
        let nowMillis = Int64(Date().timeIntervalSince1970 * 1000)
        let liveData = WledWidgetData(
            macAddress: baseData.macAddress,
            address: baseData.address,
            name: baseData.name,
            isOn: baseData.isOn,
            isOnline: baseData.isOnline,
            color: baseData.color,
            lastUpdated: nowMillis
        )

        let view = WledDeviceWidgetView(
            data: liveData,
            layoutStyle: testCase.layoutStyle
        )
        .environment(\.rendersWidgetBackgroundInView, true)

        assertSnapshot(
            of: view,
            as: .image(
                perceptualPrecision: 0.98,
                layout: .fixed(width: testCase.size.width, height: testCase.size.height),
                traits: UITraitCollection(userInterfaceStyle: testCase.style)
            ),
            named: testCase.name,
            testName: "widgetSnapshot(_:)"
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
