//
//  PreviewData.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-27.
//

#if DEBUG
import Foundation
import SwiftUI
import Shared

@MainActor
struct PreviewData {

    /// In-memory device database that preview devices are saved to, so list previews can observe them.
    static let database = AppDatabase(inMemory: true)

    static let previewVersion = VersionWithAssets(
        version: Version(
            id: 1,
            repositoryId: Repository.companion.DEFAULT_ID,
            tagName: "0.15.0",
            name: "v0.15.0 Hoshi",
            description: "## What's Changed\n\n* Cool new features by @wled",
            isPrerelease: false,
            publishedDate: "2025-01-01T00:00:00Z",
            htmlUrl: "https://github.com/wled/WLED/releases/tag/v0.15.0"
        ),
        assets: [
            Asset(
                id: 1,
                versionId: 1,
                name: "WLED_0.15.0_ESP32.bin",
                size: 1_048_576,
                downloadUrl: "https://github.com/wled/WLED/releases/download/v0.15.0/WLED_0.15.0_ESP32.bin",
                assetId: 1
            )
        ]
    )

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
        // Device is on 0.14.0, Update 0.15.0 is available in DB
        ensureVersionsExist()
        let device = createDevice(name: "Old WLED", ip: "10.0.1.14", version: "0.14.0", color: [50, 50, 255])
        device.availableUpdateVersion = "0.15.0"
        return device
    }

    static var hiddenDevice: DeviceWithState {
        createDevice(name: "Hidden Light", ip: "10.0.1.15", isHidden: true, color: [200, 0, 255])
    }

    // MARK: - Helpers

    private static func createDevice(
        name: String,
        ip: String,
        version: String = "0.15.0",
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
        let version = previewVersion.version
        let assets = previewVersion.assets
        Task {
            await database.waitUntilReady()
            guard let repo = try? await database.repositoryDao.getRepositoryByOwnerAndRepo(
                ownerAndRepo: Repository.companion.DEFAULT_OWNER_REPO
            ) else {
                return
            }
            try? await database.versionWithAssetsRepository.updateRepository(
                repository: repo,
                versionToAssetsMap: [version: assets]
            )
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
#endif
