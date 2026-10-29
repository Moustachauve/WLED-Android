import SwiftUI

struct DeviceErrorView: View {
    var onRetry: () -> Void

    private let imageSize: CGFloat = 150

    var body: some View {
        VStack(spacing: 16) {
            Spacer()

            Image(.akemi012Rip)
                .resizable()
                .scaledToFit()
                .frame(width: imageSize)
                .accessibilityHidden(true)

            VStack(spacing: 8) {
                Text("The selected device could not be reached")
                    .font(.title2)
                    .multilineTextAlignment(.center)

                Text("Make sure it is plugged in and connected to the network")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button("Retry Connection", action: onRetry)
                .buttonStyle(.borderedProminent)
                .padding(.top, 8)

            Spacer()
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

#if DEBUG
#Preview {
    DeviceErrorView(onRetry: {})
}
#endif
