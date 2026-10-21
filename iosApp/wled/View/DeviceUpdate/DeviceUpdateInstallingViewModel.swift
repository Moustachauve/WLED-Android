//
//  DeviceUpdateInstallingViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-22.
//

import Foundation
import Observation
import OSLog
import Shared

@MainActor
@Observable
final class DeviceUpdateInstallingViewModel {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceUpdateInstallingViewModel"
    )

    enum UpdateStatus: Equatable {
        case idle
        case downloading(versionName: String)
        case installing(versionName: String)
        case completed
        case failed(versionName: String, error: String)
    }

    var status: UpdateStatus = .idle

    func startUpdateProcess(
        device: DeviceWithState,
        version: VersionWithAssets,
        database: AppDatabase = .shared
    ) async {
        let updateService = DeviceUpdateService.companion.create(
            device: device.device,
            stateInfo: device.stateInfo,
            versionWithAssets: version,
            deviceApiFactory: database.deviceApiFactory,
            githubApi: database.githubApi
        )
        let assetName = updateService.getAssetName()
        let fallbackName = assetName.isEmpty ? version.version.tagName : assetName
        guard updateService.couldDetermineAsset() else {
            status = .failed(
                versionName: fallbackName,
                error: String(localized: "No Compatible Version Found")
            )
            return
        }

        let downloadFailedMessage = String(localized: "Download failed")
        do {
            if !updateService.isAssetFileCached() {
                status = .downloading(versionName: assetName)
                var didFinishDownload = false
                for await downloadState in updateService.downloadBinary(repositoryDao: database.repositoryDao) {
                    if let failed = downloadState as? DownloadState.Failed {
                        status = .failed(
                            versionName: assetName,
                            error: failed.error?.message ?? downloadFailedMessage
                        )
                        return
                    }
                    if downloadState is DownloadState.Finished {
                        didFinishDownload = true
                        break
                    }
                }
                guard didFinishDownload else {
                    status = .failed(versionName: assetName, error: downloadFailedMessage)
                    return
                }
            }

            status = .installing(versionName: assetName)
            let response = try await updateService.installUpdate()
            guard response.isSuccessful else {
                status = .failed(
                    versionName: assetName,
                    error: String(localized: "Update failed with status code: \(Int(response.code))")
                )
                return
            }

            do {
                try await database.deviceRepository.updateSkipUpdateTag(
                    macAddress: device.device.macAddress,
                    skipUpdateTag: ""
                )
            } catch {
                Self.logger.error("Failed to reset skipUpdateTag: \(error.localizedDescription)")
            }
            status = .completed
        } catch {
            if error is CancellationError || Task.isCancelled {
                status = .idle
                return
            }
            status = .failed(versionName: assetName, error: error.localizedDescription)
        }
    }

    func completeUpdateIfInstalled(
        device: DeviceWithState,
        version: VersionWithAssets,
        database: AppDatabase = .shared
    ) {
        guard status == .completed else { return }
        let rawTag = version.version.tagName
        let cleanedTag = rawTag.hasPrefix("v") ? String(rawTag.dropFirst()) : rawTag
        if let current = device.stateInfo {
            device.stateInfo = current.withVersion(newVersion: cleanedTag)
        }
        device.availableUpdateVersion = nil
        database.deviceConnectionManager.markUpdateInstalled(
            macAddress: device.device.macAddress,
            installedVersionTag: rawTag
        )
    }
}
