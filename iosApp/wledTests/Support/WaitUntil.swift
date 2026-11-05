import Foundation
import Testing

/// Polls `condition` until it's true or the timeout elapses (resilient against CI scheduling delays).
@MainActor
func waitUntil(
    timeout: Duration = .seconds(10),
    _ condition: () async throws -> Bool
) async throws {
    let clock = ContinuousClock()
    let deadline = clock.now + timeout
    while clock.now < deadline {
        if try await condition() { return }
        try await Task.sleep(for: .milliseconds(20))
    }
    Issue.record("Timed out after \(timeout) waiting for condition")
}
