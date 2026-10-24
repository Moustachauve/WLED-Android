import Foundation
import Network
import OSLog

private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
    category: "UdpSoundSyncSender"
)

/// Sends UDP Sound Sync V2 packets to WLED devices.
///
/// Sends to the multicast group (239.0.0.1:11988) and unicast to all known
/// device addresses. Uses `NWConnection` for multicast and unicast sending.
actor UdpSoundSyncSender {

    // MARK: - Constants

    private static let multicastAddress = "239.0.0.1"
    private static let port: UInt16 = 11988

    // MARK: - Properties

    private var multicastConnection: NWConnection?
    private var unicastConnections: [String: NWConnection] = [:]
    private var frameCounter: UInt8 = 0
    private var isClosed = false

    // MARK: - Public API

    init() {
        setupMulticastConnection()
    }

    /// Sends a sound sync packet to the multicast group and all unicast addresses.
    /// The packet's frame counter is overridden with an auto-incrementing value.
    func sendPacket(packetData: Data) {
        guard !isClosed else { return }

        // Increment frame counter (wraps at 256)
        frameCounter &+= 1

        // Send to multicast
        multicastConnection?.send(
            content: packetData,
            completion: .contentProcessed { error in
                if let error {
                    logger.warning("Failed to send multicast packet: \(error.localizedDescription)")
                }
            }
        )

        // Send unicast to each known device
        for (_, connection) in unicastConnections {
            connection.send(
                content: packetData,
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
        // Remove connections for addresses no longer in the list
        let newAddressSet = Set(addresses)
        for (address, connection) in unicastConnections where !newAddressSet.contains(address) {
            connection.cancel()
            unicastConnections.removeValue(forKey: address)
        }

        // Add connections for new addresses
        for address in addresses where unicastConnections[address] == nil {
            guard let connection = createUnicastConnection(to: address) else { continue }
            unicastConnections[address] = connection
        }
    }

    /// Closes all connections and releases resources.
    func close() {
        guard !isClosed else { return }
        isClosed = true
        multicastConnection?.cancel()
        multicastConnection = nil
        for (_, connection) in unicastConnections {
            connection.cancel()
        }
        unicastConnections.removeAll()
    }

    // MARK: - Private

    private func setupMulticastConnection() {
        guard let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(Self.multicastAddress),
            port: NWEndpoint.Port(integerLiteral: Self.port)
        ) else {
            logger.error("Failed to create multicast endpoint")
            return
        }

        let params = NWParameters.udp
        params.multipathServiceType = .handoff

        let connection = NWConnection(to: endpoint, using: params)
        connection.stateUpdateHandler = { [weak self] state in
            Task { [weak self] in
                await self?.handleConnectionState(state, for: "multicast")
            }
        }
        connection.start(queue: .global(qos: .userInitiated))
        multicastConnection = connection
    }

    private func createUnicastConnection(to address: String) -> NWConnection? {
        guard let endpoint = NWEndpoint.hostPort(
            host: NWEndpoint.Host(address),
            port: NWEndpoint.Port(integerLiteral: Self.port)
        ) else {
            logger.error("Failed to create unicast endpoint for \(address)")
            return nil
        }

        let params = NWParameters.udp
        let connection = NWConnection(to: endpoint, using: params)
        connection.stateUpdateHandler = { [weak self] state in
            Task { [weak self] in
                await self?.handleConnectionState(state, for: address)
            }
        }
        connection.start(queue: .global(qos: .userInitiated))
        return connection
    }

    private func handleConnectionState(_ state: NWConnection.State, for label: String) {
        switch state {
        case .ready:
            logger.debug("Connection ready: \(label)")
        case .failed(let error):
            logger.warning("Connection failed (\(label)): \(error.localizedDescription)")
        case .cancelled:
            break
        default:
            break
        }
    }
}
