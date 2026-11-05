import Foundation
@testable import WLED

@MainActor
final class FakeDiscoveryService: DeviceDiscovering {
    var onDeviceDiscovered: ((_ address: String, _ macAddress: String?) -> Void)?
    var onLocalNetworkPermissionChanged: ((_ isGranted: Bool) -> Void)?
    private(set) var scanCallCount = 0
    private(set) var stopCallCount = 0

    func scan(duration: Duration?) {
        scanCallCount += 1
    }

    func stop() {
        stopCallCount += 1
    }
}
