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

    var body: some View {
        NavigationStack {
            Form {
                descriptionSection

                if let errorMessage = viewModel.errorMessage {
                    errorSection(errorMessage)
                }

                if viewModel.isRunning {
                    visualizationSection
                }

                gainSection
            }
            .animation(.default, value: viewModel.agcEnabled)
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
            VStack(alignment: .leading, spacing: 12) {
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

                startStopControls
            }
            .padding(.vertical, 4)
        }
    }

    private var gainSection: some View {
        Section {
            VStack(alignment: .leading, spacing: 6) {
                Text("Gain")

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
            }
            .padding(.vertical, 4)

            Toggle(
                isOn: Binding(
                    get: { viewModel.agcEnabled },
                    set: { enabled in
                        withAnimation {
                            viewModel.setAgcEnabled(enabled)
                        }
                    }
                )
            ) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Automatic Gain Control")
                    Text("Automatically adjusts gain based on ambient volume")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if viewModel.agcEnabled {
                VStack(alignment: .leading, spacing: 8) {
                    Picker(
                        "Automatic Gain Control",
                        selection: Binding(
                            get: { viewModel.agcMode },
                            set: { viewModel.setAgcMode($0) }
                        )
                    ) {
                        ForEach(AgcMode.allCases, id: \.self) { mode in
                            Text(mode.localizedTitle).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                    .labelsHidden()

                    Text(viewModel.agcMode.localizedDescription)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            }

            VStack(alignment: .leading, spacing: 6) {
                let minSquelchInt = SoundSyncProcessor.Companion.shared.MIN_SQUELCH
                let maxSquelchInt = SoundSyncProcessor.Companion.shared.MAX_SQUELCH
                HStack {
                    Text("Squelch")
                    Spacer()
                    Text(verbatim: viewModel.squelch.formatted(.number))
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                    Stepper(
                        "Squelch",
                        value: Binding(
                            get: { viewModel.squelch },
                            set: { viewModel.setSquelch($0) }
                        ),
                        in: minSquelchInt...maxSquelchInt,
                        step: 1
                    )
                    .labelsHidden()
                }
                Slider(
                    value: Binding(
                        get: { Float(viewModel.squelch) },
                        set: { viewModel.updateLiveSquelch(Int32($0.rounded())) }
                    ),
                    in: Float(minSquelchInt)...Float(maxSquelchInt),
                    step: 1
                ) {
                    Text("Squelch")
                } onEditingChanged: { editing in
                    if !editing {
                        viewModel.saveSquelch()
                    }
                }
                .labelsHidden()
                Text("Ignores background noise below this threshold when no music is playing.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            .padding(.vertical, 4)
        }
    }

    private var visualizationSection: some View {
        Section("Frequency visualization") {
            FrequencyVisualizationView(
                fftChannels: viewModel.fftChannels,
                sampleLevel: viewModel.sampleLevel,
                isPeak: viewModel.isPeak
            )
        }
    }

    private func errorSection(_ message: String) -> some View {
        Section {
            Text("Sound sync error: \(message)")
                .foregroundStyle(.red)
        }
    }

    @ViewBuilder
    private var startStopControls: some View {
        if viewModel.isRunning {
            VStack(spacing: 8) {
                Text("Sound sync is running")
                    .foregroundStyle(.tint)

                Button(role: .destructive) {
                    Task {
                        await viewModel.stop()
                    }
                } label: {
                    Text("Stop Sound Sync")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
        } else if viewModel.isPermissionDenied {
            VStack(spacing: 8) {
                Text("Microphone permission is required for sound sync")
                    .foregroundStyle(.red)

                Button {
                    Task {
                        await viewModel.start()
                    }
                } label: {
                    Text("Grant Permission")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
            }
        } else {
            Button {
                Task {
                    await viewModel.start()
                }
            } label: {
                Text("Start Sound Sync")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
        }
    }
}

// MARK: - Frequency Visualization View

private struct FrequencyVisualizationView: View {
    let fftChannels: [UInt8]
    let sampleLevel: Float
    let isPeak: Bool

    @State private var smoother = VisualizationSmoother()

    var body: some View {
        TimelineView(.animation) { timeline in
            let frame = smoother.nextFrame(
                fftChannels: fftChannels,
                sampleLevel: sampleLevel,
                date: timeline.date
            )

            VStack(spacing: 12) {
                FftBarChart(normalizedChannels: frame.normalizedChannels)
                    .frame(height: chartHeight)
                    .accessibilityHidden(true)

                SampleLevelIndicator(
                    level: frame.sampleLevel,
                    isPeak: isPeak
                )
                .frame(height: 8)
                .accessibilityHidden(true)
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
    private var lastFrameDate: Date?

    func nextFrame(
        fftChannels: [UInt8],
        sampleLevel: Float,
        date: Date
    ) -> Frame {
        let deltaTimeMs: Int64
        if let lastFrameDate {
            deltaTimeMs = max(0, Int64(date.timeIntervalSince(lastFrameDate) * 1000))
        } else {
            deltaTimeMs = 0
        }
        lastFrameDate = date

        for index in 0..<Self.channelCount {
            let byteValue: UInt8 = index < fftChannels.count ? fftChannels[index] : 0
            kotlinFftChannels.set(index: Int32(index), value: Int8(bitPattern: byteValue))
        }

        let visualizer = Self.visualizer
        _ = visualizer.smoothFftChannels(
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
}

// MARK: - AgcMode Localization

private extension AgcMode {
    var localizedTitle: LocalizedStringResource {
        switch self {
        case .normal:
            "Normal"
        case .vivid:
            "Vivid"
        case .lazy:
            "Lazy"
        }
    }

    var localizedDescription: LocalizedStringResource {
        switch self {
        case .normal:
            "Smoothly follows changes in volume. A good default for most setups."
        case .vivid:
            "Reacts quickly to volume changes for a more dramatic response to dynamics."
        case .lazy:
            "Slower to adjust. Works well for GEQ effects or music with wide dynamic range."
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
