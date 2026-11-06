import Foundation
import Network
import OSLog
import Shared

private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
    category: "UdpSoundSyncSender"
)

/// Sends UDP Sound Sync V2 packets to WLED devices.
///
/// Sends unicast UDP datagrams on port 11988 to all known visible WLED device addresses.
/// On iOS 14+, sending IP multicast to `239.0.0.1` requires the restricted
/// `com.apple.developer.networking.multicast` entitlement; because WLED's Sound Sync V2
/// receiver accepts both multicast and unicast packets on port 11988, unicast delivery
/// works reliably across all discovered devices without requiring special entitlements.
actor UdpSoundSyncSender {

    // MARK: - Constants

    private static let port = UInt16(UdpSoundSyncPacket.Companion.shared.DEFAULT_PORT)
    private static let frameCounterOffset = Int(UdpSoundSyncPacket.Companion.shared.OFFSET_FRAME_COUNTER)
    private static let reconnectBackoff: Duration = .seconds(2)

    // MARK: - Properties

    private var targetAddresses: Set<String> = []
    private var unicastConnections: [String: NWConnection] = [:]
    private var retryAfter: [String: ContinuousClock.Instant] = [:]
    private var frameCounter: UInt8 = 0
    private var isClosed = false

    // MARK: - Public API

    init() {}

    /// Sends a sound sync packet to all unicast target addresses.
    /// The packet's frame counter is overridden with an auto-incrementing value.
    func sendPacket(packetData: Data) {
        guard !isClosed, packetData.count > Self.frameCounterOffset else { return }

        reconnectFailedTargetsIfNeeded()

        var payload = packetData
        payload[Self.frameCounterOffset] = frameCounter
        frameCounter &+= 1

        for (_, connection) in unicastConnections where connection.state == .ready {
            connection.send(
                content: payload,
                completion: .contentProcessed { error in
                    if let error {
                        logger.warning("Failed to send unicast packet: \(error.localizedDescription)")
                    }
                }
            )
        }
    }

    /// Updates the list of unicast target addresses (e.g., when the device list changes).
    func updateUnicastAddresses(_ addresses: [String]) {
        guard !isClosed else { return }

        let newAddressSet = Set(addresses)
        targetAddresses = newAddressSet

        for (address, connection) in unicastConnections where !newAddressSet.contains(address) {
            connection.stateUpdateHandler = nil
            connection.cancel()
            unicastConnections.removeValue(forKey: address)
            retryAfter.removeValue(forKey: address)
        }

        for address in newAddressSet where unicastConnections[address] == nil {
            retryAfter.removeValue(forKey: address)
            guard let connection = createConnection(to: address, label: address) else { continue }
            unicastConnections[address] = connection
        }
    }

    /// Closes all connections and releases resources.
    func close() {
        guard !isClosed else { return }
        isClosed = true
        targetAddresses.removeAll()
        retryAfter.removeAll()
        for (_, connection) in unicastConnections {
            connection.stateUpdateHandler = nil
            connection.cancel()
        }
        unicastConnections.removeAll()
    }

    // MARK: - Private

    private func reconnectFailedTargetsIfNeeded() {
        guard !retryAfter.isEmpty else { return }
        let now = ContinuousClock.now
        for address in targetAddresses where unicastConnections[address] == nil {
            if let nextRetry = retryAfter[address], now < nextRetry {
                continue
            }
            retryAfter.removeValue(forKey: address)
            if let connection = createConnection(to: address, label: address) {
                unicastConnections[address] = connection
            }
        }
    }

    private func createConnection(to host: String, label: String) -> NWConnection? {
        guard let nwPort = NWEndpoint.Port(rawValue: Self.port) else {
            logger.error("Invalid UDP port: \(Self.port)")
            return nil
        }
        let endpoint = NWEndpoint.hostPort(host: NWEndpoint.Host(host), port: nwPort)
        let connection = NWConnection(to: endpoint, using: .udp)
        connection.stateUpdateHandler = { [weak self] state in
            Task { [weak self] in
                await self?.handleConnectionState(state, for: label)
            }
        }
        connection.start(queue: .global(qos: .userInitiated))
        return connection
    }

    private func handleConnectionState(_ state: NWConnection.State, for label: String) {
        guard !isClosed else { return }
        switch state {
        case .ready:
            logger.debug("Connection ready: \(label)")
        case .failed(let error):
            logger.warning("Connection failed (\(label)): \(error.localizedDescription)")
            if let connection = unicastConnections.removeValue(forKey: label) {
                connection.stateUpdateHandler = nil
                connection.cancel()
                if targetAddresses.contains(label) {
                    retryAfter[label] = ContinuousClock.now + Self.reconnectBackoff
                }
            }
        default:
            break
        }
    }
}
