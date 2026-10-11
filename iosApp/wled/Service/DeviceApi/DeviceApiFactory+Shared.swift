//
//  DeviceApiFactory+Shared.swift
//  WLED
//

import Shared

extension DeviceApiFactory {
    /// Process-wide factory so every device request shares one Darwin `HttpClient`
    /// (and therefore one `URLSession` and its connection pool).
    static let shared: DeviceApiFactory = DeviceApiFactory.companion.create()
}

// The underlying Ktor HttpClient is safe to use from any thread.
extension DeviceApiFactory: @retroactive @unchecked Sendable {}
