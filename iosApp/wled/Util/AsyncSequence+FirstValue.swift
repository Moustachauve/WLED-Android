import Foundation

extension AsyncSequence {
    /// Returns the first element emitted by the sequence, or `nil` if it finishes without emitting or throws.
    ///
    /// Useful for reading the current value of a Kotlin `Flow` (bridged by SKIE) without keeping a collector alive.
    func firstValue() async -> Element? {
        do {
            for try await value in self {
                return value
            }
        } catch {}
        return nil
    }
}
