//
//  DeviceAddViewModel.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-21.
//

import Foundation
import Combine
import Shared

@MainActor
final class DeviceAddViewModel: ObservableObject {

    @Published var address: String = ""
    @Published var currentStep: Step = .form()
    private let firstContactService: DeviceFirstContactService
    private let addressValidator: AddressValidator

    init(
        firstContactService: DeviceFirstContactService = AppDatabase.shared.deviceFirstContactService,
        addressValidator: AddressValidator = AppDatabase.shared.addressValidator
    ) {
        self.firstContactService = firstContactService
        self.addressValidator = addressValidator
    }

    func submitCreateDevice() {
        guard let valid = addressValidator.validate(rawAddress: address) as? AddressValidationResult.Valid else {
            currentStep = .form(errorMessage: Error.enterValidAddress)
            return
        }
        let sanitizedAddress = valid.sanitizedAddress
        Task {
            await findDevice(sanitizedAddress: sanitizedAddress)
        }
    }

    /// Starts searching for the device and adds it, if one is found
    private func findDevice(sanitizedAddress: String) async {
        currentStep = .adding
        do {
            let newDevice = try await firstContactService.fetchAndUpsertDevice(
                rawAddress: sanitizedAddress
            )
            currentStep = .success(device: newDevice)
        } catch let error {
            print("Error: \(error)")
            currentStep = .form(errorMessage: Error.cantConnect)
        }
    }

    // MARK: - State enum
    enum Step: Equatable {
        case form(errorMessage: String = "")
        case adding
        case success(device: Device)

        var isForm: Bool {
            if case .form = self { return true }
            return false
        }
    }

    // MARK: - Struct with magic stuff
    struct Error {
        static let enterValidAddress = String(localized: "Please enter a valid address")
        static let cantConnect = String(localized: "Could not connect to the device. Verify the address")
    }
}
