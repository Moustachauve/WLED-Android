import Shared
import SwiftUI

private typealias State = SwiftUI.State

private let chartHeight: CGFloat = 120
private let channelMaxValue: Float = 255
private let barGapRatio: CGFloat = 0.15

struct SoundSyncView: View {

    @Bindable var viewModel: SoundSyncViewModel
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase

    @State private var visualizationSmoother = VisualizationSmoother()
    @State private var visualizationReferenceDate = Date.now

    var body: some View {
        NavigationStack {
            Form {
                descriptionSection
                startStopSection

                if let errorMessage = viewModel.errorMessage {
                    errorSection(errorMessage)
                }

                if viewModel.isRunning {
                    visualizationSection
                }

                gainSection
            }
            .navigationTitle("Sound Sync")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    HStack(spacing: 6) {
                        Text("Sound Sync")
                            .font(.headline)
                        Text("BETA")
                            .font(.caption2.weight(.semibold))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.accentColor.opacity(0.15))
                            .foregroundStyle(.tint)
                            .clipShape(Capsule())
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
            .onAppear {
                viewModel.refreshPermissionState()
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .active {
                    viewModel.refreshPermissionState()
                }
            }
        }
    }

    // MARK: - Sections

    private var descriptionSection: some View {
        Section {
            HStack(spacing: 12) {
                Image(systemName: "waveform")
                    .foregroundStyle(.tint)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Send audio-reactive data to your WLED devices using UDP Sound Sync. Devices must be set to Receive mode in their audio sync settings.")
                        .font(.subheadline)
                    Text("This feature is currently in beta and may change in future updates.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private var gainSection: some View {
        Section("Gain") {
            HStack {
                Slider(
                    value: Binding(
                        get: { viewModel.displayedGain },
                        set: { viewModel.updateLiveGain($0) }
                    ),
                    in: 0...GainControl.Companion.shared.MAX_MANUAL_GAIN
                ) {
                    Text("Gain")
                } onEditingChanged: { editing in
                    if !editing {
                        viewModel.saveGain()
                    }
                }
                .labelsHidden()
                .disabled(viewModel.agcEnabled)
                .animation(viewModel.agcEnabled ? .smooth : nil, value: viewModel.displayedGain)

                Text("\(viewModel.displayedGain.formatted(.number.precision(.fractionLength(1))))x")
                    .monospacedDigit()
            }

            Toggle(
                "Automatic Gain Control",
                isOn: Binding(
                    get: { viewModel.agcEnabled },
                    set: { viewModel.setAgcEnabled($0) }
                )
            )
        }
    }

    private var visualizationSection: some View {
        Section("Frequency visualization") {
            TimelineView(.animation) { timeline in
                let elapsedTimeMs = Int64(timeline.date.timeIntervalSince(visualizationReferenceDate) * 1000)
                let frame = visualizationSmoother.nextFrame(
                    fftChannels: viewModel.fftChannels,
                    sampleLevel: viewModel.sampleLevel,
                    elapsedTimeMs: elapsedTimeMs
                )

                VStack(spacing: 12) {
                    FftBarChart(normalizedChannels: frame.normalizedChannels)
                        .frame(height: chartHeight)
                        .accessibilityHidden(true)

                    SampleLevelIndicator(
                        level: frame.sampleLevel,
                        isPeak: viewModel.isPeak
                    )
                    .frame(height: 8)
                    .accessibilityHidden(true)
                }
            }
            .onAppear {
                visualizationReferenceDate = Date.now
                visualizationSmoother.reset()
            }
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
                        Task {
                            await viewModel.stop()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
                }
            } else if viewModel.isPermissionDenied {
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
    let normalizedChannels: [Float]

    var body: some View {
        GeometryReader { geometry in
            if !normalizedChannels.isEmpty {
                let channelCount = CGFloat(normalizedChannels.count)
                let barWidth = geometry.size.width / channelCount
                let gap = barWidth * barGapRatio
                let actualBarWidth = barWidth - gap

                HStack(spacing: gap) {
                    ForEach(0..<normalizedChannels.count, id: \.self) { index in
                        let value = CGFloat(normalizedChannels[index])
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

// MARK: - Visualization Smoother

@MainActor
private final class VisualizationSmoother {
    struct Frame {
        let normalizedChannels: [Float]
        let sampleLevel: Float
    }

    private static let visualizer = SoundSyncIconVisualizer.shared
    private static let channelCount = Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT)

    private let kotlinFftChannels = KotlinByteArray(size: Int32(channelCount))
    private let kotlinSmoothedChannels = KotlinFloatArray(size: Int32(channelCount))
    private var smoothedChannels: [Float] = Array(repeating: 0, count: channelCount)
    private var smoothedSampleLevel: Float = 0
    private var lastElapsedTimeMs: Int64 = 0

    func nextFrame(
        fftChannels: [UInt8],
        sampleLevel: Float,
        elapsedTimeMs: Int64
    ) -> Frame {
        let deltaTimeMs = max(0, elapsedTimeMs - lastElapsedTimeMs)
        lastElapsedTimeMs = elapsedTimeMs

        for index in 0..<Self.channelCount {
            let byteValue: UInt8 = index < fftChannels.count ? fftChannels[index] : 0
            kotlinFftChannels.set(index: Int32(index), value: Int8(bitPattern: byteValue))
        }

        let visualizer = Self.visualizer
        _ = visualizer.smoothFftChannels(
            isRunning: true,
            fftChannels: kotlinFftChannels,
            deltaTimeMs: deltaTimeMs,
            smoothedOutput: kotlinSmoothedChannels
        )

        for index in 0..<Self.channelCount {
            smoothedChannels[index] = kotlinSmoothedChannels.get(index: Int32(index))
        }

        let clampedTargetLevel = min(max(sampleLevel, 0), channelMaxValue)
        smoothedSampleLevel = visualizer.smoothBarLevel(
            previous: smoothedSampleLevel,
            target: clampedTargetLevel,
            isRunning: true,
            deltaTimeMs: deltaTimeMs
        )

        return Frame(
            normalizedChannels: smoothedChannels,
            sampleLevel: smoothedSampleLevel
        )
    }

    func reset() {
        lastElapsedTimeMs = 0
        smoothedSampleLevel = 0
        for index in 0..<Self.channelCount {
            kotlinSmoothedChannels.set(index: Int32(index), value: 0)
            smoothedChannels[index] = 0
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
