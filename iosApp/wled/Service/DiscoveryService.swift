import Foundation
import Network
import OSLog

@MainActor
protocol DeviceDiscovering: AnyObject {
    var onDeviceDiscovered: ((_ address: String, _ macAddress: String?) -> Void)? { get set }
    var onLocalNetworkPermissionChanged: ((_ isGranted: Bool) -> Void)? { get set }

    func scan(duration: Duration?)
    func stop()
}

extension DeviceDiscovering {
    func scan() {
        scan(duration: .seconds(10))
    }
}

@MainActor
final class DiscoveryService: DeviceDiscovering {

    private static let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
        category: "DiscoveryService"
    )

    var onDeviceDiscovered: ((_ address: String, _ macAddress: String?) -> Void)?
    var onLocalNetworkPermissionChanged: ((_ isGranted: Bool) -> Void)?

    private var browser: NWBrowser?
    private var activeConnections: [ObjectIdentifier: NWConnection] = [:]
    private var autoStopTask: Task<Void, Never>?

    /// Tracks whether the local network permission has been granted.
    /// `nil` = unknown (checking), `true` = granted, `false` = denied.
    private(set) var isLocalNetworkGranted: Bool? {
        didSet {
            if let isLocalNetworkGranted, oldValue != isLocalNetworkGranted {
                onLocalNetworkPermissionChanged?(isLocalNetworkGranted)
            }
        }
    }

    init(
        onDeviceDiscovered: ((_ address: String, _ macAddress: String?) -> Void)? = nil,
        onLocalNetworkPermissionChanged: ((_ isGranted: Bool) -> Void)? = nil
    ) {
        self.onDeviceDiscovered = onDeviceDiscovered
        self.onLocalNetworkPermissionChanged = onLocalNetworkPermissionChanged
    }

    isolated deinit {
        stop()
    }

    func scan(duration: Duration? = .seconds(10)) {
        stop()

        let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(type: "_wled._tcp", domain: "local.")
        let parameters = NWParameters()
        parameters.allowLocalEndpointReuse = true
        parameters.acceptLocalOnly = true
        parameters.allowFastOpen = true

        let browser = NWBrowser(for: descriptor, using: parameters)
        self.browser = browser

        browser.stateUpdateHandler = { [weak self, weak browser] state in
            Task { @MainActor in
                guard let self, self.browser === browser else { return }
                self.handleBrowserState(state)
            }
        }

        browser.browseResultsChangedHandler = { [weak self, weak browser] results, changes in
            Task { @MainActor in
                guard let self, self.browser === browser else { return }
                self.handleBrowseResults(results, changes)
            }
        }

        browser.start(queue: DispatchQueue.main)

        if let duration {
            autoStopTask = Task { [weak self] in
                try? await Task.sleep(for: duration)
                guard !Task.isCancelled else { return }
                self?.stop()
            }
        }
    }

    func stop() {
        autoStopTask?.cancel()
        autoStopTask = nil

        browser?.stateUpdateHandler = nil
        browser?.browseResultsChangedHandler = nil
        browser?.cancel()
        browser = nil

        for connection in activeConnections.values {
            connection.stateUpdateHandler = nil
            connection.cancel()
        }
        activeConnections.removeAll()
    }

    // MARK: - Browser Handling

    private func handleBrowserState(_ newState: NWBrowser.State) {
        switch newState {
        case .failed(let error):
            Self.logger.error("NWBrowser entered failed state: \(error.localizedDescription)")
            stop()
        case .ready:
            Self.logger.debug("NWBrowser ready for Bonjour discovery")
            isLocalNetworkGranted = true
        case .waiting(let error):
            Self.logger.debug("NWBrowser waiting: \(error.localizedDescription)")
            // DNS error -65570 is kDNSServiceErr_PolicyDenied, which means
            // the user has denied Local Network permission for this app.
            if case .dns(let dnsError) = error, dnsError == -65570 {
                Self.logger.warning("Local network permission denied by user")
                isLocalNetworkGranted = false
            }
        case .setup:
            Self.logger.debug("NWBrowser in setup state")
        default:
            break
        }
    }

    private func handleBrowseResults(_ results: Set<NWBrowser.Result>, _ changes: Set<NWBrowser.Result.Change>) {
        Self.logger.debug("NWBrowser scan results count: \(results.count)")

        for change in changes {
            if case .added(let result) = change {
                resolveService(for: result)
            }
        }
    }

    private func resolveService(for result: NWBrowser.Result) {
        let macAddress: String?
        if case .bonjour(let txtRecord) = result.metadata {
            macAddress = txtRecord["mac"]
        } else {
            macAddress = nil
        }

        if case .service(let name, _, _, _) = result.endpoint {
            Self.logger.debug("Resolving endpoint for \(name), MAC: \(macAddress ?? "nil")")
            let connection = NWConnection(to: result.endpoint, using: .tcp)
            let connectionId = ObjectIdentifier(connection)
            activeConnections[connectionId] = connection

            connection.stateUpdateHandler = { [weak self] state in
                Task { @MainActor [weak self, macAddress] in
                    self?.handleConnectionState(
                        state,
                        connection: connection,
                        name: name,
                        macAddress: macAddress
                    )
                }
            }
            connection.start(queue: .global())
        }
    }

    private func handleConnectionState(
        _ state: NWConnection.State,
        connection: NWConnection,
        name: String,
        macAddress: String?
    ) {
        let connectionId = ObjectIdentifier(connection)
        guard activeConnections[connectionId] != nil else { return }

        switch state {
        case .ready:
            if let innerEndpoint = connection.currentPath?.remoteEndpoint,
               case .hostPort(let host, let port) = innerEndpoint {
                let remoteHost = String("\(host)".split(separator: "%")[0])
                Self.logger.info("Resolved \(name) at \(remoteHost):\(port.rawValue)")
                onDeviceDiscovered?(remoteHost, macAddress)
            }
            cancelAndRemove(connection)
        case .failed(let error):
            Self.logger.debug("Failed to resolve \(name): \(error.localizedDescription)")
            cancelAndRemove(connection)
        case .cancelled:
            activeConnections.removeValue(forKey: connectionId)
        default:
            break
        }
    }

    private func cancelAndRemove(_ connection: NWConnection) {
        let connectionId = ObjectIdentifier(connection)
        activeConnections.removeValue(forKey: connectionId)
        connection.stateUpdateHandler = nil
        connection.cancel()
    }
}
