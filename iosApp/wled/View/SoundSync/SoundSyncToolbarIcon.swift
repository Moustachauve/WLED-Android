import Shared
import SwiftUI

private let iconSize: CGFloat = 22
private let viewportSize: CGFloat = 24
private let barWidthUnits: CGFloat = 2
private let barStepUnits: CGFloat = 4
private let firstBarXUnits: CGFloat = 3
private let maxBarHeightUnits: CGFloat = 20

/// Sound Sync toolbar icon that displays `"waveform.badge.mic"` when inactive and
/// animates the 5 rounded waveform bars in `.accentColor` when Sound Sync is running.
struct SoundSyncToolbarIcon: View {
    var viewModel: SoundSyncViewModel?

    @State private var smoother = BarLevelSmoother()
    @State private var referenceDate = Date.now

    var body: some View {
        if viewModel?.isRunning == true {
            TimelineView(.animation) { timeline in
                let elapsedTimeMs = Int64(timeline.date.timeIntervalSince(referenceDate) * 1000)
                let fftChannels = viewModel?.fftChannels ?? []
                let barLevels = smoother.nextLevels(
                    isRunning: true,
                    fftChannels: fftChannels,
                    elapsedTimeMs: elapsedTimeMs
                )

                Image(
                    size: CGSize(width: iconSize, height: iconSize),
                    label: Text("Sound Sync")
                ) { context in
                    let unitScaleX = iconSize / viewportSize
                    let unitScaleY = iconSize / viewportSize
                    let barWidth = barWidthUnits * unitScaleX
                    let maxBarHeight = maxBarHeightUnits * unitScaleY
                    let centerY = iconSize / 2
                    let cornerRadius = barWidth / 2

                    for index in 0..<barLevels.count {
                        let barHeight = maxBarHeight * CGFloat(barLevels[index])
                        let left = (firstBarXUnits + CGFloat(index) * barStepUnits) * unitScaleX
                        let top = centerY - barHeight / 2
                        let rect = CGRect(x: left, y: top, width: barWidth, height: barHeight)

                        context.fill(
                            Path(roundedRect: rect, cornerRadius: cornerRadius),
                            with: .color(.accentColor)
                        )
                    }
                }
                .renderingMode(.original)
            }
            .onAppear {
                referenceDate = Date.now
                smoother.reset()
            }
        } else {
            Label("Sound Sync", systemImage: "waveform.badge.mic")
        }
    }
}

@MainActor
private final class BarLevelSmoother {
    private static let visualizer = SoundSyncIconVisualizer.shared
    private static let barCount = Int(visualizer.BAR_COUNT)

    private var smoothedLevels: [Float] = (0..<barCount).map { index in
        visualizer.restingBarLevel(barIndex: Int32(index))
    }

    func nextLevels(
        isRunning: Bool,
        fftChannels: [UInt8],
        elapsedTimeMs: Int64
    ) -> [Float] {
        let visualizer = Self.visualizer
        for index in 0..<Self.barCount {
            let barIndex = Int32(index)
            let peakChannelValue = isRunning
                ? Self.bandPeakChannelValue(
                    fftChannels: fftChannels,
                    start: Int(visualizer.bandChannelStart(barIndex: barIndex)),
                    endInclusive: Int(visualizer.bandChannelEndInclusive(barIndex: barIndex))
                )
                : 0
            let target = visualizer.computeBarLevel(
                barIndex: barIndex,
                isRunning: isRunning,
                bandPeakChannelValue: Int32(peakChannelValue),
                elapsedTimeMs: elapsedTimeMs
            )
            let current = visualizer.smoothBarLevel(
                previous: smoothedLevels[index],
                target: target,
                isRunning: isRunning
            )
            smoothedLevels[index] = current
        }
        return smoothedLevels
    }

    func reset() {
        let visualizer = Self.visualizer
        for index in 0..<Self.barCount {
            smoothedLevels[index] = visualizer.restingBarLevel(barIndex: Int32(index))
        }
    }

    private static func bandPeakChannelValue(
        fftChannels: [UInt8],
        start: Int,
        endInclusive: Int
    ) -> Int {
        guard !fftChannels.isEmpty, start <= endInclusive else { return 0 }
        var maxChannelValue = 0
        let upperBound = min(endInclusive, fftChannels.count - 1)
        if start <= upperBound {
            for channel in start...upperBound {
                let value = Int(fftChannels[channel])
                if value > maxChannelValue {
                    maxChannelValue = value
                }
            }
        }
        return maxChannelValue
    }
}
