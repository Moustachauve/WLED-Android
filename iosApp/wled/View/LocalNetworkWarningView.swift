import SwiftUI

/// A warning banner displayed at the top of the device list when Local Network
/// permission has been denied by the user. Not dismissable — it automatically
/// disappears when the permission is granted.
struct LocalNetworkWarningView: View {

    var onOpenSettings: () -> Void

    @ScaledMetric private var iconSize: CGFloat = 45

    var body: some View {
        Card(style: .warning) {
            Image(systemName: "exclamationmark.triangle.fill")
                .resizable()
                .scaledToFit()
                .frame(height: iconSize)
                .foregroundStyle(.red)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 4)
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: 6) {

                Text("Local Network Access Required")
                    .font(.headline)
                    .foregroundStyle(.primary)

                Text("local_network_warning_body")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .fixedSize(horizontal: false, vertical: true)

                Text("local_network_instructions")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .italic()
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(.bottom, 12)

            Button(action: onOpenSettings) {
                Label("Open Settings", systemImage: "gear")
                    .font(.headline)
                    .frame(maxWidth: .infinity)
                    .foregroundStyle(.white)
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .tint(.red)
        }
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(.red.opacity(0.5), lineWidth: 1.5)
        )
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }
}

#Preview {
    LocalNetworkWarningView {
        print("Open Settings tapped")
    }
    .padding()
}
