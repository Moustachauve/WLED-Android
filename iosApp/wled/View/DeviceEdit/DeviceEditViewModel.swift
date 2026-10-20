//
//  DeviceEditViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-23.
//

import Foundation
import Combine
import OSLog
import Shared

@MainActor
class DeviceEditViewModel: ObservableObject {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceEditViewModel"
    )

    private let database: AppDatabase
    private let deviceRepository: DeviceRepository

    private var cancellables = Set<AnyCancellable>()

    @Published var device: DeviceWithState

    @Published var customName: String = ""
    @Published var hideDevice: Bool = false
    @Published var branch: Branch = .unknown

    @Published var isCheckingForUpdates: Bool = false

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared
    ) {
        self.database = database
        self.deviceRepository = database.deviceRepository
        self.device = device
        customName = device.device.customName
        hideDevice = device.device.isHidden
        branch = device.device.branch

        setupCustomNameDebouncedListener()
        setupHideDeviceListener()
        setupBranchListener()
    }

    // MARK: - Form change listeners

    /// Saves the custom name every seconds when there are changes to the value
    private func setupCustomNameDebouncedListener() {
        $customName
            .debounce(for: .seconds(0.5), scheduler: RunLoop.main)
            .sink { [weak self] newCustomName in
                guard let self = self else { return }

                // Check if the value actually changed from the saved value
                // to prevent saving when the view first loads
                if self.device.device.customName != newCustomName {
                    self.device.device = self.device.device.copy(customName: newCustomName)
                    self.persist { repository, mac in
                        try await repository.updateCustomName(macAddress: mac, customName: newCustomName)
                    }
                }
            }
            .store(in: &cancellables)
    }

    private func setupHideDeviceListener() {
        $hideDevice
            .removeDuplicates() // Only fire if the bool actually flips
            .sink { [weak self] isHidden in
                guard let self = self else { return }

                // Check against the source of truth to prevent loops
                if self.device.device.isHidden != isHidden {
                    self.device.device = self.device.device.copy(isHidden: isHidden)
                    self.persist { repository, mac in
                        try await repository.updateIsHidden(macAddress: mac, isHidden: isHidden)
                    }
                }
            }
            .store(in: &cancellables)
    }

    private func setupBranchListener() {
        $branch
            .removeDuplicates()
            .sink { [weak self] newBranch in
                guard let self = self else { return }

                if self.device.device.branch != newBranch {
                    // Changing branch also clears the skipped version (done atomically by updateBranch)
                    self.device.device = self.device.device.copy(skipUpdateTag: "", branch: newBranch)
                    self.persist { repository, mac in
                        try await repository.updateBranch(macAddress: mac, branch: newBranch)
                    }
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Public API

    func checkForUpdate() async {
        isCheckingForUpdates = true
        defer { isCheckingForUpdates = false }

        let mac = device.device.macAddress
        device.device = device.device.copy(skipUpdateTag: "")
        do {
            try await deviceRepository.updateSkipUpdateTag(macAddress: mac, skipUpdateTag: "")
        } catch {
            Self.logger.error("Failed to clear skipUpdateTag: \(error.localizedDescription)")
        }

        await database.refreshVersions(for: device.device)

        if let stateInfo = device.stateInfo {
            let resolvedTag = try? await database.deviceUpdateManager.checkForUpdate(
                device: device.device,
                stateInfo: stateInfo
            )
            device.availableUpdateVersion = resolvedTag
        }
    }

    /// Persists a single field so concurrent writers (e.g. websocket updates) can't overwrite it with a stale copy.
    private func persist(_ write: @escaping @Sendable (DeviceRepository, String) async throws -> Void) {
        let repository = deviceRepository
        let mac = device.device.macAddress
        Task {
            do {
                try await write(repository, mac)
            } catch {
                Self.logger.error("Unresolved error saving device: \(error.localizedDescription)")
            }
        }
    }
}
