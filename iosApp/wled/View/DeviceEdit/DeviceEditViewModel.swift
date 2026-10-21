//
//  DeviceEditViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-23.
//

import Foundation
import Observation
import OSLog
import Shared

@MainActor
@Observable
final class DeviceEditViewModel {
    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceEditViewModel"
    )

    private let database: AppDatabase
    private let deviceRepository: DeviceRepository
    private let customNameDebounceDuration: Duration

    @ObservationIgnored private var customNameDebounceTask: Task<Void, Never>?

    var device: DeviceWithState

    var customName: String = "" {
        didSet {
            guard customName != oldValue else { return }
            scheduleCustomNameSave(customName)
        }
    }

    var hideDevice: Bool = false {
        didSet {
            guard hideDevice != oldValue, device.device.isHidden != hideDevice else { return }
            let isHidden = hideDevice
            device.device = device.device.copy(isHidden: isHidden)
            persist { repository, mac in
                try await repository.updateIsHidden(macAddress: mac, isHidden: isHidden)
            }
        }
    }

    var branch: Branch = .unknown {
        didSet {
            guard branch != oldValue, device.device.branch != branch else { return }
            let newBranch = branch
            // Changing branch also clears the skipped version (done atomically by updateBranch)
            device.device = device.device.copy(skipUpdateTag: "", branch: newBranch)
            persist { repository, mac in
                try await repository.updateBranch(macAddress: mac, branch: newBranch)
            }
        }
    }

    var isCheckingForUpdates: Bool = false

    init(
        device: DeviceWithState,
        database: AppDatabase = .shared,
        customNameDebounceDuration: Duration = .milliseconds(500)
    ) {
        self.database = database
        self.deviceRepository = database.deviceRepository
        self.customNameDebounceDuration = customNameDebounceDuration
        self.device = device
        self.customName = device.device.customName
        self.hideDevice = device.device.isHidden
        self.branch = device.device.branch
    }

    // MARK: - Form change listeners

    /// Debounces custom name edits before persisting to the repository.
    private func scheduleCustomNameSave(_ newCustomName: String) {
        customNameDebounceTask?.cancel()
        let duration = customNameDebounceDuration
        customNameDebounceTask = Task { [weak self] in
            do {
                try await Task.sleep(for: duration)
            } catch {
                return
            }
            guard let self, !Task.isCancelled else { return }
            guard self.device.device.customName != newCustomName else { return }

            self.device.device = self.device.device.copy(customName: newCustomName)
            self.persist { repository, mac in
                try await repository.updateCustomName(macAddress: mac, customName: newCustomName)
            }
        }
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
