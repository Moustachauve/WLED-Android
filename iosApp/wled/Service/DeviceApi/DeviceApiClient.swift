//
//  DeviceApiClient.swift
//  WLED
//

import Shared

/// Owns the process-wide Kotlin `DeviceApiFactory` so every device request shares one Darwin
/// `HttpClient` (and therefore one `URLSession` and its connection pool).
///
/// `@unchecked Sendable`: the factory is immutable after init and Ktor's `HttpClient` is safe to use
/// from any thread. The conformance lives on this app-owned type rather than retroactively on the
/// Kotlin class.
final class DeviceApiClient: @unchecked Sendable {
    static let shared = DeviceApiClient(factory: DeviceApiFactory.companion.create())

    let factory: DeviceApiFactory

    init(factory: DeviceApiFactory) {
        self.factory = factory
    }

    /// Creates the shared Kotlin `DeviceApi` for a device address.
    func create(address: String) -> any DeviceApi {
        factory.create(address: address)
    }
}
