import SwiftUI

private let chartHeight: CGFloat = 120
private let channelMaxValue: Float = 255
private let barGapRatio: CGFloat = 0.15

struct SoundSyncView: View {

    @State private var viewModel: SoundSyncViewModel
    @Environment(\.dismiss) private var dismiss

    init(viewModel: SoundSyncViewModel) {
        _viewModel = State(wrappedValue: viewModel)
    }

    var body: some View {
        NavigationStack {
            Form {
                descriptionSection
                gainSection

                if viewModel.isRunning {
                    visualizationSection
                }

                if let errorMessage = viewModel.errorMessage {
                    errorSection(errorMessage)
                }

                startStopSection
            }
            .navigationTitle("Sound Sync")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .task {
                viewModel.refreshPermissionState()
                await viewModel.observe()
            }
        }
    }

    // MARK: - Sections

    private var descriptionSection: some View {
        Section {
            HStack(spacing: 12) {
                Image(systemName: "waveform")
                    .foregroundStyle(.tint)
                Text("Send audio-reactive data to your WLED devices using UDP Sound Sync. Devices must be set to Receive mode in their audio sync settings.")
                    .font(.subheadline)
            }
        }
    }

    private var gainSection: some View {
        Section("Gain") {
            HStack {
                Slider(
                    value: $viewModel.gain,
                    in: 0...10,
                    onEditingChanged: { editing in
                        if !editing {
                            viewModel.setGain(viewModel.gain)
                        }
                    }
                )
                .disabled(viewModel.agcEnabled)

                Text(String(format: "%.1fx", viewModel.gain))
                    .monospacedDigit()
                    .frame(width: 44, alignment: .trailing)
            }

            Toggle("Automatic Gain Control", isOn: $viewModel.agcEnabled)
                .onChange(of: viewModel.agcEnabled) { _, newValue in
                    viewModel.setAgcEnabled(newValue)
                }
        }
    }

    private var visualizationSection: some View {
        Section("Frequency visualization") {
            FftBarChart(channels: viewModel.fftChannels)
                .frame(height: chartHeight)

            SampleLevelIndicator(
                level: viewModel.sampleLevel,
                isPeak: viewModel.isPeak
            )
            .frame(height: 8)
        }
    }

    private func errorSection(_ message: String) -> some View {
        Section {
            Text("Sound sync error: \(message)")
                .foregroundStyle(.red)
        }
    }

    private var startStopSection: some View {
        Section {
            if viewModel.isRunning {
                VStack(spacing: 8) {
                    Text("Sound sync is running")
                        .foregroundStyle(.tint)

                    Button("Stop Sound Sync", role: .destructive) {
                        viewModel.stop()
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                }
            } else if !viewModel.hasRecordAudioPermission {
                VStack(spacing: 8) {
                    Text("Microphone permission is required for sound sync")
                        .foregroundStyle(.red)

                    Button("Grant Permission") {
                        Task {
                            await viewModel.start()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                }
            } else {
                Button("Start Sound Sync") {
                    Task {
                        await viewModel.start()
                    }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - FFT Bar Chart

private struct FftBarChart: View {
    let channels: [UInt8]

    var body: some View {
        GeometryReader { geometry in
            let channelCount = CGFloat(channels.count)
            guard channelCount > 0 else { return }

            let barWidth = geometry.size.width / channelCount
            let gap = barWidth * barGapRatio
            let actualBarWidth = barWidth - gap

            HStack(spacing: gap) {
                ForEach(0..<channels.count, id: \.self) { index in
                    let value = CGFloat(channels[index]) / CGFloat(channelMaxValue)
                    let barHeight = value * geometry.size.height

                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.accentColor)
                        .frame(width: actualBarWidth, height: max(barHeight, 2))
                        .frame(maxHeight: .infinity, alignment: .bottom)
                }
            }
        }
    }
}

// MARK: - Sample Level Indicator

private struct SampleLevelIndicator: View {
    let level: Float
    let isPeak: Bool

    var body: some View {
        GeometryReader { geometry in
            let normalizedLevel = CGFloat(min(max(level, 0), channelMaxValue)) / CGFloat(channelMaxValue)
            let levelWidth = normalizedLevel * geometry.size.width

            ZStack(alignment: .leading) {
                RoundedRectangle(cornerRadius: 4)
                    .fill(Color.secondary.opacity(0.3))

                RoundedRectangle(cornerRadius: 4)
                    .fill(isPeak ? Color.red : Color.accentColor)
                    .frame(width: max(levelWidth, 2))
            }
        }
    }
}

// MARK: - Preview

#if DEBUG
#Preview {
    SoundSyncView(
        viewModel: SoundSyncViewModel(
            userPreferencesRepository: AppDatabase.shared.userPreferencesRepository,
            deviceRepository: AppDatabase.shared.deviceRepository
        )
    )
}
#endif
