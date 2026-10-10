//
//  DeviceEditViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-23.
//

import Foundation
import CoreData
import Combine

@MainActor
class DeviceEditViewModel: ObservableObject {
    private let context: NSManagedObjectContext

    private var cancellables = Set<AnyCancellable>()

    @Published var device: DeviceWithState

    @Published var customName: String = ""
    @Published var hideDevice: Bool = false
    @Published var branch: Branch = .unknown

    @Published var isCheckingForUpdates: Bool = false

    init(device: DeviceWithState, context: NSManagedObjectContext) {
        self.context = context
        self.device = device
        customName = device.device.customName ?? ""
        hideDevice = device.device.isHidden
        branch = device.device.branchValue

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
                    self.device.device.customName = newCustomName
                    self.saveDevice()
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
                    self.device.device.isHidden = isHidden
                    self.saveDevice()
                }
            }
            .store(in: &cancellables)
    }

    private func setupBranchListener() {
        $branch
            .removeDuplicates()
            .sink { [weak self] newBranch in
                guard let self = self else { return }

                if self.device.device.branchValue != newBranch {
                    self.device.device.branchValue = newBranch
                    self.device.device.skipUpdateTag = ""
                    self.saveDevice()
                }
            }
            .store(in: &cancellables)
    }

    // MARK: - Public API

    func checkForUpdate() async {
        isCheckingForUpdates = true
        print("Refreshing available Releases")
        await ReleaseService(context: context).refreshVersions()
        UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: WLEDNativeApp.dateLastUpdateKey)

        device.device.skipUpdateTag = ""
        isCheckingForUpdates = false
        saveDevice()
    }

    private func saveDevice() {
        do {
            try context.save()
        } catch {
            let nsError = error as NSError
            print("Unresolved error saving device: \(nsError), \(nsError.userInfo)")
        }
    }
}
