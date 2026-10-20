//
//  DeviceUpdateInstallingViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-22.
//

import Foundation
import Combine
import OSLog
import Shared

@MainActor
class DeviceUpdateInstallingViewModel: ObservableObject {
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

    @Published var status: UpdateStatus = .idle

    func startUpdateProcess(
        device: DeviceWithState,
        version: VersionWithAssets,
        database: AppDatabase = .shared,
        cacheDirPath: String? = nil
    ) async {
        let updateService = DeviceUpdateService.companion.create(
            device: device.device,
            stateInfo: device.stateInfo,
            versionWithAssets: version,
            deviceApiFactory: database.deviceApiFactory,
            githubApi: database.githubApi,
            cacheDirPath: cacheDirPath
        )
        let determinedAssetName = updateService.getAssetName()
        let fallbackName = determinedAssetName.isEmpty ? version.version.tagName : determinedAssetName
        guard updateService.couldDetermineAsset() else {
            status = .failed(
                versionName: fallbackName,
                error: String(localized: "No Compatible Version Found")
            )
            return
        }

        let assetName = updateService.getAssetName()
        do {
            if !updateService.isAssetFileCached() {
                status = .downloading(versionName: assetName)
                var didFinishDownload = false
                for await downloadState in updateService.downloadBinary(repositoryDao: database.repositoryDao) {
                    if let failed = downloadState as? DownloadState.Failed {
                        status = .failed(
                            versionName: assetName,
                            error: failed.error?.message ?? String(localized: "Download failed")
                        )
                        return
                    }
                    if downloadState is DownloadState.Finished {
                        didFinishDownload = true
                        break
                    }
                }
                guard didFinishDownload else {
                    status = .failed(versionName: assetName, error: String(localized: "Download failed"))
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
            do {
                try await database.deviceRepository.updateSkipUpdateTag(
                    macAddress: device.device.macAddress,
                    skipUpdateTag: ""
                )
            } catch {
                Self.logger.error("Failed to reset skipUpdateTag: \(error.localizedDescription)")
            }
            status = .completed
        } catch is CancellationError {
            return
        } catch {
            status = .failed(versionName: assetName, error: error.localizedDescription)
        }
    }
}
