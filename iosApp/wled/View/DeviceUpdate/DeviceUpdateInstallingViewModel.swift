//
//  DeviceUpdateInstallingViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-22.
//

import Foundation

@MainActor
class DeviceUpdateInstallingViewModel: ObservableObject {
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
        version: Version
    ) async {
        let updateService = DeviceUpdateService(
            device: device,
            version: version
        )
        do {
            guard updateService.couldDetermineAsset else {
                status = .failed(versionName: "", error: String(localized: "No Compatible Version Found"))
                return
            }
            if !updateService.isAssetFileCached() {
                status = .downloading(versionName: updateService.getAssetName())
                let success = await updateService.downloadBinary()
                guard success else {
                    status = .failed(versionName: updateService.getAssetName(), error: String(localized: "Download failed"))
                    return
                }
            }
            status = .installing(versionName: updateService.getAssetName())
            try await updateService.installUpdate()
            
            if let rawTag = version.tagName {
                let cleanedTag = rawTag.hasPrefix("v") ? String(rawTag.dropFirst()) : rawTag
                device.stateInfo?.info.version = cleanedTag
            }
            status = .completed
        } catch {
            status = .failed(versionName: updateService.getAssetName(), error: error.localizedDescription)
        }
    }
}
