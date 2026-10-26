import Testing
@testable import WLED

@MainActor
struct DeviceInfoTwoRowsTests {

    @Test(arguments: [
        (0.0, "battery.0percent"),
        (10.0, "battery.0percent"),
        (25.0, "battery.25percent"),
        (35.0, "battery.25percent"),
        (50.0, "battery.50percent"),
        (60.0, "battery.50percent"),
        (75.0, "battery.75percent"),
        (85.0, "battery.75percent"),
        (90.0, "battery.100percent"),
        (100.0, "battery.100percent")
    ])
    func batteryIconNameMapsPercentageThresholds(percentage: Double, expectedIcon: String) {
        #expect(DeviceInfoTwoRows.batteryIconName(for: percentage) == expectedIcon)
    }
}
