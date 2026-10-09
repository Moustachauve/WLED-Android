//
//  WebsocketStatusIndicatorView.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-23.
//

import SwiftUI

struct WebsocketStatusIndicator: View {

    @State private var isRotating = false

    var currentStatus: WebsocketStatus = .disconnected

    // Use ScaledMetric so the icon scales with Dynamic Type (System Font Size)
    @ScaledMetric(relativeTo: .subheadline) var size: CGFloat = 10

    private let opticalScale: CGFloat = 0.75

    var body: some View {
        Group {
            switch currentStatus {
            case .disconnected:
                RoundedRectangle(cornerRadius: size * 0.2)
                    .stroke(.red, lineWidth: size * 0.18)
                    .saturation(0.5)
                    .rotationEffect(.degrees(45))
                    .scaleEffect(opticalScale)
                    .accessibilityLabel("Status: Offline")
            case .connected:
                Circle()
                    .foregroundStyle(.tint)
                    .aspectRatio(1.0, contentMode: .fit)
                    .accessibilityLabel("Status: Connected")
            case .connecting:
                let cornerRadius = size * 0.1
                ZStack {
                    RoundedRectangle(cornerRadius: cornerRadius)
                    RoundedRectangle(cornerRadius: cornerRadius)
                        .rotationEffect(.degrees(45))
                }
                .rotationEffect(Angle(degrees: isRotating ? 360 : 0))
                .scaleEffect(opticalScale)
                .accessibilityLabel("Status: Connecting")
                .onAppear {
                    guard !isRotating else { return }
                    withAnimation(
                        .fastOutSlowIn(duration: 1.5)
                        .repeatForever(autoreverses: false)
                    ) {
                        isRotating = true
                    }
                }
                .onDisappear {
                    isRotating = false
                }
            }
        }
        .frame(width: size, height: size)
        .aspectRatio(1.0, contentMode: .fit)
    }
}

extension Animation {
    // Replicating the exact CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
    static func fastOutSlowIn(duration: TimeInterval = 0.3) -> Animation {
        Animation.timingCurve(0.4, 0.0, 0.2, 1.0, duration: duration)
    }
}

#Preview {
    VStack(spacing: 20) {
        HStack {
            WebsocketStatusIndicator(
                currentStatus: WebsocketStatus.disconnected
            )
            Text("Offline")
                .font(.subheadline.leading(.tight))
        }
        HStack {
            WebsocketStatusIndicator(currentStatus: WebsocketStatus.connected)
            Text("Online")
                .font(.subheadline.leading(.tight))
        }
        HStack {
            WebsocketStatusIndicator(currentStatus: WebsocketStatus.connecting)
            Text("Connecting")
                .font(.subheadline.leading(.tight))
        }
    }
}
