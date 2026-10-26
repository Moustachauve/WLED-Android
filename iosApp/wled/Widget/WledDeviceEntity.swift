import AppIntents
import Foundation
import Shared

/// AppEntity representing a saved WLED device for Shortcuts, Siri, Control Center, and Widgets.
struct WledDeviceEntity: AppEntity, Hashable, Sendable {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "WLED Device")
    static let defaultQuery = WledDeviceEntityQuery()

    /// The device's unique MAC address.
    let id: String
    let name: String
    let address: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(
            title: "\(name)",
            subtitle: "\(address)",
            image: .init(systemName: "lightbulb.led.fill")
        )
    }
}

extension WledDeviceEntity {
    init(device: Device) {
        self.init(
            id: device.macAddress,
            name: device.displayName,
            address: device.address
        )
    }
}

/// Entity query resolving `WledDeviceEntity` instances from the shared Room `DeviceRepository`.
struct WledDeviceEntityQuery: EntityStringQuery {
    func entities(for identifiers: [WledDeviceEntity.ID]) async throws -> [WledDeviceEntity] {
        await AppDatabase.shared.waitUntilReady()
        let ids = Set(identifiers)
        return try await AppDatabase.shared.deviceRepository.getAllDevices()
            .filter { ids.contains($0.macAddress) }
            .map(WledDeviceEntity.init(device:))
    }

    func entities(matching string: String) async throws -> [WledDeviceEntity] {
        let all = try await suggestedEntities()
        let query = string.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return all }
        return all.filter {
            $0.name.localizedStandardContains(query) || $0.address.localizedStandardContains(query)
        }
    }

    func suggestedEntities() async throws -> [WledDeviceEntity] {
        await AppDatabase.shared.waitUntilReady()
        let devices = try await AppDatabase.shared.deviceRepository.getAllDevices()
        return devices
            .filter { !$0.isHidden }
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
            .map(WledDeviceEntity.init(device:))
    }
}
