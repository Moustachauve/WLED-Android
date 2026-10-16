//
//  PreviewData.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-27.
//

import Foundation
import SwiftUI
import CoreData
import Shared

@MainActor
struct PreviewData {

    static var viewContext: NSManagedObjectContext {
        return PersistenceController.preview.container.viewContext
    }

    /// In-memory device database that preview devices are saved to, so list previews can observe them.
    static let database = AppDatabase(inMemory: true)

    // MARK: - Devices

    static var onlineDevice: DeviceWithState {
        createDevice(name: "WLED Beam", ip: "10.0.1.12")
    }

    static var offlineDevice: DeviceWithState {
        // Set last seen to 2 hours ago
        let twoHoursAgo = Int64(Date().addingTimeInterval(-7200).timeIntervalSince1970 * 1000)
        let device = createDevice(name: "WLED Strip", ip: "10.0.1.13", lastSeen: twoHoursAgo)
        device.websocketStatus = .disconnected
        return device
    }

    static var deviceWithUpdate: DeviceWithState {
        // Device is on 0.13.0, Update 0.14.0 is available in DB
        ensureVersionsExist()
        let device = createDevice(name: "Old WLED", ip: "10.0.1.14", version: "0.13.0", color: [50, 50, 255])
        // Force the available update version for preview purposes since the Combine pipeline might be async
        device.availableUpdateVersion = "0.14.0"
        return device
    }

    static var hiddenDevice: DeviceWithState {
        createDevice(name: "Hidden Light", ip: "10.0.1.15", isHidden: true, color: [200, 0, 255])
    }

    // MARK: - Helpers

    private static func createDevice(
        name: String,
        ip: String,
        version: String = "0.14.0",
        isHidden: Bool = false,
        color: [Int] = [255, 160, 0],
        lastSeen: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> DeviceWithState {
        let macAddress = "mock:mac:\(ip)"
        let device = Device(
            macAddress: macAddress,
            address: ip,
            isHidden: isHidden,
            originalName: name,
            lastSeen: lastSeen
        )
        // Save so list previews observing the database pick it up (insert replaces on conflict)
        Task {
            try? await database.deviceRepository.insert(device: device)
        }

        let deviceWithState = DeviceWithState(initialDevice: device)
        deviceWithState.websocketStatus = .connected
        deviceWithState.stateInfo = .mock(name: name, version: version, color: color)

        return deviceWithState
    }

    private static func ensureVersionsExist() {
        // Create a mock update version in CoreData if needed
        // Assuming 'Version' entity exists and has 'tagName'
        let request = NSFetchRequest<NSFetchRequestResult>(entityName: "Version")
        request.predicate = NSPredicate(format: "tagName == %@", "0.14.0")

        if let count = try? viewContext.count(for: request), count == 0 {
            let version = Version(context: viewContext)
            version.tagName = "0.14.0"
            version.name = "v0.14.0 Hoshi"
            version.versionDescription = "## What's Changed\n\n* Cool new features by @Aircoookie"
            version.isPrerelease = false
            version.publishedDate = Date()
            try? viewContext.save()
        }
    }
}

// MARK: - Mock Data Extensions

extension DeviceStateInfo {
    static func mock(name: String, version: String, color: [Int]) -> DeviceStateInfo {
        let r = Int32(color.indices.contains(0) ? color[0] : 255)
        let g = Int32(color.indices.contains(1) ? color[1] : 160)
        let b = Int32(color.indices.contains(2) ? color[2] : 0)

        return DeviceStateInfo.companion.createMock(
            name: name,
            version: version,
            red: r,
            green: g,
            blue: b,
            brightness: 128,
            isOn: true
        )
    }
}
