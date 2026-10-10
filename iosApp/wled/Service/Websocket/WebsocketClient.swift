import Foundation
import CoreData
import Combine

@MainActor
class WebsocketClient: NSObject, ObservableObject, URLSessionWebSocketDelegate {
    
    // MARK: - Properties
    
    // The state holder visible to the UI
    @Published var deviceState: DeviceWithState

    private var webSocketTask: URLSessionWebSocketTask?
    nonisolated let urlSession: URLSession
    private let delegateProxy: WeakSessionDelegate

    // State flags
    private var isManuallyDisconnected = false
    private var isConnecting = false
    private var retryCount = 0
    
    // Constants
    private let tag = "WebsocketClient"
    private let reconnectionDelay: TimeInterval = 2.5
    private let maxReconnectionDelay: TimeInterval = 60.0
    
    // Coders
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    var onDeviceStateUpdated: ((DeviceStateInfo) -> Void)?

    // MARK: - Initialization
    
    init(device: Device) {
        self.deviceState = DeviceWithState(initialDevice: device)

        let proxy = WeakSessionDelegate()
        self.delegateProxy = proxy
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 15.0
        configuration.timeoutIntervalForResource = 30.0
        configuration.waitsForConnectivity = false
        self.urlSession = URLSession(
            configuration: configuration,
            delegate: proxy,
            delegateQueue: OperationQueue.main
        )

        super.init()

        // Now that 'self' is fully initialized, connect the delegate
        self.delegateProxy.delegate = self
    }

    // MARK: - Connection Logic
    
    func connect() {
        if webSocketTask != nil || isConnecting {
            print("\(tag): Already connected or connecting to \(deviceState.device.address ?? "nil")")
            return
        }
        
        guard let address = deviceState.device.address, !address.isEmpty else {
            print("\(tag): Device address is empty")
            return
        }
        
        isManuallyDisconnected = false
        isConnecting = true
        
        DispatchQueue.main.async {
            self.deviceState.websocketStatus = .connecting
        }
        
        let urlString = "ws://\(address)/ws"
        guard let url = URL(string: urlString) else {
            print("\(tag): Invalid URL \(urlString)")
            return
        }
        
        print("\(tag): Connecting to \(address)")
        let request = URLRequest(url: url, timeoutInterval: 10)

        webSocketTask = urlSession.webSocketTask(with: request)
        webSocketTask?.resume()
        
        // Start listening for messages
        listen()
    }
    
    func disconnect() {
        print("\(tag): Manually disconnecting from \(deviceState.device.address ?? "")")
        isManuallyDisconnected = true
        
        webSocketTask?.cancel(with: .normalClosure, reason: Data("Client disconnected".utf8))
        webSocketTask = nil
        
        DispatchQueue.main.async {
            self.deviceState.websocketStatus = .disconnected
            self.isConnecting = false
        }
    }
    
    private func reconnect() {
        if isManuallyDisconnected || isConnecting { return }
        
        let delayTime = min(reconnectionDelay * pow(2.0, Double(retryCount)), maxReconnectionDelay)
        print("\(tag): Reconnecting to \(deviceState.device.address ?? "") in \(delayTime)s")
        
        Task {
            try await Task.sleep(for: .seconds(delayTime))
            if !isManuallyDisconnected && !isConnecting {
                self.retryCount += 1
                self.connect()
            }
        }
    }
    
    // MARK: - Message Handling
    
    private func listen() {
        webSocketTask?.receive { [weak self] result in
            Task {
                guard let self = self else { return }

                switch result {
                case .failure(let error):
                    await self.handleFailure(error)
                case .success(let message):
                    switch message {
                    case .string(let text):
                        await self.handleMessage(text)
                    case .data(let data):
                        // WLED mostly sends text, but good to handle data
                        if let text = String(data: data, encoding: .utf8) {
                            await self.handleMessage(text)
                        }
                    @unknown default:
                        break
                    }

                    // Recursively listen for the next message
                    await self.listen()
                }
            }
        }
    }
    
    private func handleMessage(_ text: String) {
        let decoder = self.decoder
        Task.detached(priority: .userInitiated) { [weak self] in
            guard let self = self else { return }
            guard let data = text.data(using: .utf8) else { return }

            do {
                let info = try decoder.decode(DeviceStateInfo.self, from: data)
                await MainActor.run {
                    self.deviceState.stateInfo = info

                    // If we get a message, we are connected (fallback if onOpen didn't fire)
                    if self.isConnecting {
                        self.deviceState.websocketStatus = .connected
                        self.isConnecting = false
                    }
                    self.onDeviceStateUpdated?(info)
                }
            } catch {
                print("Failed to parse JSON: \(error)")
            }
        }
    }
    
    private func handleFailure(_ error: Error) {
        print("\(tag): WebSocket failure: \(error)")
        webSocketTask = nil
        
        DispatchQueue.main.async {
            self.deviceState.websocketStatus = .disconnected
            self.isConnecting = false
            self.reconnect()
        }
    }
    
    // MARK: - Sending
    
    /// Sends a State object to the device.
    /// Note: Kotlin code used `State` class. In Swift, assuming `WLEDStateChange` or `WledState` is the equivalent Encodable struct.
    func sendState(_ state: WledState) {
        if deviceState.websocketStatus != .connected {
            print("\(tag): Not connected to \(deviceState.device.address ?? ""), reconnecting...")
            connect()
        }
        
        do {
            let data = try encoder.encode(state)
            if let jsonString = String(data: data, encoding: .utf8) {
                print("\(tag): Sending message: \(jsonString)")
                let message = URLSessionWebSocketTask.Message.string(jsonString)
                
                webSocketTask?.send(message) { error in
                    if let error = error {
                        Task {
                            print("\(self.tag): Failed to send message: \(error)")
                            await self.handleFailure(error)
                        }
                    }
                }
            }
        } catch {
            print("\(tag): Failed to encode state: \(error)")
        }
    }

    func destroy() {
        print("\(tag): Websocket client destroyed")
        disconnect()
        urlSession.invalidateAndCancel()
    }

    deinit {
        print("WebsocketClient deinit")
        urlSession.invalidateAndCancel()
    }

    // MARK: - URLSessionWebSocketDelegate
    
    nonisolated func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        // Jump to MainActor to update state
        Task { @MainActor in
            print("\(self.tag): WebSocket connected")
            self.deviceState.websocketStatus = .connected
            self.retryCount = 0
            self.isConnecting = false
        }
    }

    nonisolated func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        Task { @MainActor in
            let reasonString = reason.flatMap { String(data: $0, encoding: .utf8) } ?? "No reason"
            print("\(self.tag): WebSocket closing. Code: \(closeCode), reason: \(reasonString)")

            self.deviceState.websocketStatus = .disconnected

            if closeCode != .normalClosure && !self.isManuallyDisconnected {
                self.reconnect()
            }
        }
    }
}

// MARK: - WeakSessionDelegate

// Helper to break the strong reference cycle between URLSession and WebsocketClient
// @unchecked Sendable Justification:
// 1. Problem: This class inherits `Sendable` conformance from `NSObject` but has a mutable `delegate` property, which triggers a concurrency warning.
// 2. Safety: We manually verify thread safety because this delegate is exclusively used by a URLSession configured with `OperationQueue.main`.
// 3. Conclusion: All access to the mutable `delegate` property is guaranteed to occur on the main thread, making the compiler's strict check unnecessary here.
final class WeakSessionDelegate: NSObject, URLSessionWebSocketDelegate, @unchecked Sendable {
    weak var delegate: URLSessionWebSocketDelegate?

    init(_ delegate: URLSessionWebSocketDelegate? = nil) {
        self.delegate = delegate
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        delegate?.urlSession?(session, webSocketTask: webSocketTask, didOpenWithProtocol: `protocol`)
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        delegate?.urlSession?(session, webSocketTask: webSocketTask, didCloseWith: closeCode, reason: reason)
    }
}
