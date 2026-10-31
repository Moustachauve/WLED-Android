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
    private let database: AppDatabase

    init() {
        self.init(database: .shared)
    }

    init(database: AppDatabase) {
        self.database = database
    }

    func entities(for identifiers: [WledDeviceEntity.ID]) async throws -> [WledDeviceEntity] {
        await database.waitUntilReady()
        let ids = Set(identifiers)
        return try await database.deviceRepository.getAllDevices()
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
        await database.waitUntilReady()
        return try await database.deviceRepository.getAllDevices()
            .sorted { $0.displayName.localizedCaseInsensitiveCompare($1.displayName) == .orderedAscending }
            .map(WledDeviceEntity.init(device:))
    }

    func defaultResult() async -> WledDeviceEntity? {
        await database.waitUntilReady()
        guard let allDevices = try? await database.deviceRepository.getAllDevices() else {
            return nil
        }
        let visibleDevices = allDevices.filter { !$0.isHidden }
        let candidates = visibleDevices.isEmpty ? allDevices : visibleDevices
        guard candidates.count == 1, let singleDevice = candidates.first else {
            return nil
        }
        return WledDeviceEntity(device: singleDevice)
    }
}
