//
//  PreviewData.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-27.
//

import Foundation
import SwiftUI
import CoreData

@MainActor
struct PreviewData {

    static var viewContext: NSManagedObjectContext {
        return PersistenceController.preview.container.viewContext
    }

    // MARK: - Devices

    static var onlineDevice: DeviceWithState {
        createDevice(name: "WLED Beam", ip: "10.0.1.12")
    }

    static var offlineDevice: DeviceWithState {
        let device = createDevice(name: "WLED Strip", ip: "10.0.1.13")
        device.websocketStatus = .disconnected
        // Set last seen to 2 hours ago
        device.device.lastSeen = Int64(Date().addingTimeInterval(-7200).timeIntervalSince1970 * 1000)
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
        color: [Int] = [255, 160, 0]
    ) -> DeviceWithState {
        let macAddress = "mock:mac:\(ip)"
        let request: NSFetchRequest<Device> = Device.fetchRequest()
        request.predicate = NSPredicate(format: "macAddress == %@", macAddress)

        var device: Device!

        // 1. Try to find existing device
        if let results = try? viewContext.fetch(request), let existing = results.first {
            device = existing
        } else {
            device = Device(context: viewContext)
            device.macAddress = macAddress
        }

        // Always update properties (so code changes reflect immediately in preview)
        device.originalName = name
        device.address = ip
        device.isHidden = isHidden

        let deviceWithState = DeviceWithState(initialDevice: device)
        deviceWithState.websocketStatus = .connected
        deviceWithState.stateInfo = .mock(name: name, version: version, color: color)

        // Save to ensure ID is stable
        if viewContext.hasChanges {
            try? viewContext.save()
        }

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
        let r = color.indices.contains(0) ? color[0] : 255
        let g = color.indices.contains(1) ? color[1] : 160
        let b = color.indices.contains(2) ? color[2] : 0

        let json = """
        {
            "state": {
                "on": true,
                "bri": 128,
                "seg": [
                    {"id": 0, "col": [[\(r), \(g), \(b)], [0, 0, 0], [0, 0, 0]]}
                ]
            },
            "info": {
                "name": "\(name)",
                "ver": "\(version)",
                "leds": { "count": 30, "pwr": 0, "fps": 0, "maxpwr": 0, "maxseg": 0 },
                "wifi": { "signal": -60 }
            }
        }
        """
        guard let data = json.data(using: .utf8),
              let decoded = try? JSONDecoder().decode(DeviceStateInfo.self, from: data) else {
            fatalError("Failed to decode mock DeviceStateInfo")
        }
        return decoded
    }
}
