//
//  WebsocketClientMock.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-27.
//

import Foundation

class MockWebsocketClient: WebsocketClient {
    override func connect() {
        // Simulate "Connecting" state
        DispatchQueue.main.async {
            self.deviceState.websocketStatus = .connecting
        }

        // Determine if we want this mock to be online or offline
        // You can check the name, IP, or just make them all online.
        let isOfflineDevice = deviceState.device.originalName == "WLED Strip" // Matches 'offlineDevice' in PreviewData

        // Generate a random color!
        let randomColor = [
            Int.random(in: 50...255), // R
            Int.random(in: 50...255), // G
            Int.random(in: 50...255)  // B
        ]

        DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
            if isOfflineDevice {
                self.deviceState.websocketStatus = .disconnected
            } else {
                self.deviceState.websocketStatus = .connected
                // Inject mock state info (colors, brightness) so the UI updates
                let name = self.deviceState.device.originalName ?? "Mock Device"
                self.deviceState.stateInfo = .mock(name: name, version: "0.14.0", color: randomColor)
            }
        }
    }

    override func disconnect() {
        DispatchQueue.main.async {
            self.deviceState.websocketStatus = .disconnected
        }
    }

    override func sendState(_ state: WledState) {
        print("MockClient: Ignoring sendState \(state)")
    }
}
