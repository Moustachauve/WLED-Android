//
//  EmptyDeviceListView.swift
//  wled
//
//  Created by Christophe Gagnier on 2026-01-05.
//

import SwiftUI

struct EmptyDeviceListView: View {
    @Binding var addDeviceButtonActive: Bool
    @Binding var showHiddenDevices: Bool
    var hasHiddenDevices: Bool

    private let imageSize: CGFloat = 150

    var body: some View {
        VStack {
            Spacer()

            VStack {
                Image(.akemi018Teeth)
                    .resizable()
                    .scaledToFit()
                    .frame(width: imageSize)
                    .accessibilityHidden(true)

                Text("You don't have any visible devices")
                    .font(.title2)
                    .multilineTextAlignment(.center)
            }
            // This allows the "Pull to Refresh" drag gesture to pass through
            // these views and reach the underlying List.
            .allowsHitTesting(false)

            Button("Add a New Device", systemImage: "plus") {
                addDeviceButtonActive = true
            }
            .buttonStyle(.borderedProminent)

            if hasHiddenDevices && !showHiddenDevices {
                VStack {
                    Text("Some of your devices are hidden")
                        .font(.subheadline)
                        .foregroundColor(.secondary)

                    Button {
                        withAnimation {
                            showHiddenDevices = true
                        }
                    } label: {
                        Label("Show Hidden Devices", systemImage: "eye")
                    }
                    .buttonStyle(.borderless)
                }
                .padding(.top, 32)
            }

            Spacer()
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#Preview {
    ZStack {
        EmptyDeviceListView(
            addDeviceButtonActive: .constant(false),
            showHiddenDevices: .constant(false),
            hasHiddenDevices: true
        )
    }
}
