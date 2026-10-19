import Foundation
import Network

// TODO: Check if this needs a start/stop like on Android
@MainActor
final class DiscoveryService {

    let onDeviceDiscovered: (_ address: String, _ macAddress: String?) -> Void
    let onLocalNetworkPermissionChanged: (_ isGranted: Bool) -> Void
    private var browser: NWBrowser?

    /// Tracks whether the local network permission has been granted.
    /// `nil` = unknown (checking), `true` = granted, `false` = denied.
    private(set) var isLocalNetworkGranted: Bool? {
        didSet {
            if let isLocalNetworkGranted, oldValue != isLocalNetworkGranted {
                onLocalNetworkPermissionChanged(isLocalNetworkGranted)
            }
        }
    }

    init(
        onDeviceDiscovered: @escaping (_ address: String, _ macAddress: String?) -> Void,
        onLocalNetworkPermissionChanged: @escaping (_ isGranted: Bool) -> Void = { _ in }
    ) {
        self.onDeviceDiscovered = onDeviceDiscovered
        self.onLocalNetworkPermissionChanged = onLocalNetworkPermissionChanged
    }

    isolated deinit {
        browser?.cancel()
    }

    func scan() {
        browser?.cancel()

        let descriptor = NWBrowser.Descriptor.bonjourWithTXTRecord(type: "_wled._tcp", domain: "local.")
        let parameters = NWParameters()
        parameters.allowLocalEndpointReuse = true
        parameters.acceptLocalOnly = true
        parameters.allowFastOpen = true

        let browser = NWBrowser(for: descriptor, using: parameters)
        self.browser = browser

        browser.stateUpdateHandler = { [weak self] state in
            Task { @MainActor in
                self?.handleBrowserState(state)
            }
        }

        browser.browseResultsChangedHandler = { [weak self] results, changes in
            Task { @MainActor in
                self?.handleBrowseResults(results, changes)
            }
        }

        browser.start(queue: DispatchQueue.main)
    }

    // MARK: - Browser Handling

    private func handleBrowserState(_ newState: NWBrowser.State) {
        switch newState {
        case .failed(let error):
            print("NW Browser: now in Error state: \(error)")
            self.browser?.cancel()
        case .ready:
            print("NW Browser: new bonjour discovery - ready")
            isLocalNetworkGranted = true
        case .waiting(let error):
            print("NW Browser: waiting with error: \(error)")
            // DNS error -65570 is kDNSServiceErr_PolicyDenied, which means
            // the user has denied Local Network permission for this app.
            if case .dns(let dnsError) = error, dnsError == -65570 {
                print("NW Browser: Local network permission denied")
                isLocalNetworkGranted = false
            }
        case .setup:
            print("NW Browser: in SETUP state")
        default:
            break
        }
    }

    private func handleBrowseResults(_ results: Set<NWBrowser.Result>, _ changes: Set<NWBrowser.Result.Change>) {
        print("NW Browser: Scan results found:")
        for result in results {
            print(result.endpoint.debugDescription)
        }

        // TODO: Since we get the IP and mac from the first contact, check if we can skip resolving the IP address and use the mDNS address directly for first contact
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
        print("NW Browser: Added, mac: \(macAddress?.description ?? "nil")")

        if case .service(let name, _, _, _) = result.endpoint {
            print("Connecting to \(name), MAC: \(macAddress?.description ?? "nil")")
            let connection = NWConnection(to: result.endpoint, using: .tcp)
            connection.stateUpdateHandler = { [weak self] state in
                Task { @MainActor [weak self, macAddress] in
                    self?.handleConnectionState(state, connection: connection, name: name, macAddress: macAddress)
                }
            }
            connection.start(queue: .global())
        }
    }

    private func handleConnectionState(_ state: NWConnection.State, connection: NWConnection, name: String, macAddress: String?) {
        switch state {
        case .ready:
            if let innerEndpoint = connection.currentPath?.remoteEndpoint,
               case .hostPort(let host, let port) = innerEndpoint {
                let remoteHost = "\(host)".split(separator: "%")[0]
                print("Connected to \(name) at", "\(remoteHost):\(port)")

                self.onDeviceDiscovered("\(remoteHost)", macAddress)
            }
        default:
            break
        }
    }
}
