//
//  DeviceFirstContactService.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-16.
//

import Foundation
import Shared
import OSLog

/// Service responsible for handling the first contact with a device.
/// It fetches device info and handles the creation or update of the Device record in the repository.
actor DeviceFirstContactService {

    private let repository: DeviceRepository
    private let urlSession: URLSession
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DeviceFirstContactService"
    )

    enum ServiceError: LocalizedError {
        case invalidURL
        case missingMacAddress
        case networkError(Error)

        var errorDescription: String? {
            switch self {
            case .invalidURL:
                return String(localized: "The device address is invalid.", comment: "Invalid URL error")
            case .missingMacAddress:
                return String(localized: "The device did not report a valid MAC address.", comment: "Missing MAC error")
            case .networkError(let error):
                return String(localized: "Network error: \(error.localizedDescription)")
            }
        }
    }

    /// - Parameters:
    ///   - repository: The Room device repository.
    ///   - urlSession: Injected session for testability (defaults to .shared).
    init(repository: DeviceRepository = AppDatabase.shared.deviceRepository, urlSession: URLSession = .shared) {
        self.repository = repository
        self.urlSession = urlSession
    }

    // MARK: - Public API

    /// Fetches device information using its address, then ensures a corresponding
    /// device record exists in the database (creating or updating its address
    /// as necessary).
    ///
    /// - Parameter rawAddress: The network address input (e.g., "http://192.168.1.1/" or "wled.local").
    /// - Returns: The Device that was created or updated.
    func fetchAndUpsertDevice(rawAddress: String) async throws -> Device {
        let cleanAddress = sanitize(address: rawAddress)

        logger.debug("Initiating contact with: \(cleanAddress)")
        let info = try await fetchDeviceInfo(address: cleanAddress)

        guard let macAddress = info.mac, !macAddress.isEmpty else {
            logger.error("Could not retrieve MAC address for device at \(cleanAddress)")
            throw ServiceError.missingMacAddress
        }

        return try await upsertDevice(macAddress: macAddress, hostname: cleanAddress, name: info.name)
    }

    /// Attempts to identify and update a device using only the MAC address from mDNS/Discovery.
    /// This avoids a network call to the device if we already know who it is.
    ///
    /// - Parameters:
    ///   - macAddress: The MAC address found via mDNS (can be null/empty).
    ///   - address: The new IP address.
    /// - Returns: true if the device was found and processed (updated or skipped), false otherwise.
    func tryUpdateAddress(macAddress: String?, address: String) async -> Bool {
        guard let macAddress, !macAddress.isEmpty else { return false }

        // Ensure the address provided by mDNS is clean before saving
        let cleanAddress = sanitize(address: address)
        guard let existingDevice = try? await repository.findDeviceByMacAddress(address: macAddress) else {
            return false
        }

        if existingDevice.address != cleanAddress {
            logger.info("Fast update: IP changed for \(existingDevice.originalName) (\(macAddress))")
            do {
                try await repository.updateAddress(macAddress: existingDevice.macAddress, address: cleanAddress)
            } catch {
                logger.error("Failed to save fast update: \(error.localizedDescription)")
            }
        }
        return true
    }

    // MARK: - Private Helpers

    /// Removes schemes (http/https) and trailing slashes to ensure we store a clean hostname/IP.
    private func sanitize(address: String) -> String {
        var result = address

        // Remove scheme if present
        if let range = result.range(of: "://") {
            result = String(result[range.upperBound...])
        }

        // Remove trailing slashes
        while result.hasSuffix("/") {
            result.removeLast()
        }

        return result
    }

    /// Fetches device information from the specified address.
    private func fetchDeviceInfo(address: String) async throws -> Info {
        // Construct URL, ensuring http scheme and json/info path
        let urlString = "http://\(address)/json/info"

        guard let url = URL(string: urlString) else {
            throw ServiceError.invalidURL
        }

        var request = URLRequest(url: url)
        request.timeoutInterval = 10
        request.cachePolicy = .reloadIgnoringLocalAndRemoteCacheData

        do {
            let (data, _) = try await urlSession.data(for: request)
            return try JSONDecoder().decode(Info.self, from: data)
        } catch {
            throw ServiceError.networkError(error)
        }
    }

    /// Handles the repository logic to find, update, or create the device.
    private func upsertDevice(macAddress: String, hostname: String, name: String?) async throws -> Device {
        guard let existingDevice = try await repository.findDeviceByMacAddress(address: macAddress) else {
            logger.info("Creating new device: \(macAddress)")
            let newDevice = Device(
                macAddress: macAddress,
                address: hostname,
                originalName: name ?? "",
                lastSeen: 0
            )
            try await repository.insert(device: newDevice)
            return newDevice
        }

        let deviceName = name ?? existingDevice.originalName
        // Check if updates are actually needed to minimize database writes
        if existingDevice.address == hostname && existingDevice.originalName == deviceName {
            logger.debug("Device exists and is up to date: \(macAddress)")
            return existingDevice
        }

        logger.debug("Updating existing device: \(macAddress)")
        let storedMac = existingDevice.macAddress
        if existingDevice.address != hostname {
            try await repository.updateAddress(macAddress: storedMac, address: hostname)
        }
        if existingDevice.originalName != deviceName {
            try await repository.updateOriginalName(macAddress: storedMac, originalName: deviceName)
        }
        return existingDevice.copy(address: hostname, originalName: deviceName)
    }
}
