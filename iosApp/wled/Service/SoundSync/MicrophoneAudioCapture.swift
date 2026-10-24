import AVFAudio
import Foundation
import OSLog

private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
    category: "MicrophoneAudioCapture"
)

/// Captures audio from the device microphone using `AVAudioEngine`.
///
/// Emits normalized Float samples in [-1.0, 1.0] at 44100 Hz, mono.
/// Requires the microphone permission (`NSMicrophoneUsageDescription`).
actor MicrophoneAudioCapture {

    // MARK: - Properties

    private let engine = AVAudioEngine()
    private var isCapturing = false
    private var onAudioData: (@Sendable ([Float]) -> Void)?

    // MARK: - Public API

    /// Checks whether the microphone permission has been granted.
    nonisolated func hasPermission() -> Bool {
        AVAudioApplication.shared.recordPermission == .granted
    }

    /// Requests microphone permission. Returns `true` if already granted or granted by the user.
    nonisolated func requestPermission() async -> Bool {
        await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
    }

    /// Starts audio capture. The `onAudioData` callback is invoked on a background queue
    /// with chunks of normalized PCM samples.
    ///
    /// - Parameter onAudioData: Callback invoked with audio sample chunks.
    /// - Returns: `true` if capture started successfully.
    func start(onAudioData: @escaping @Sendable ([Float]) -> Void) -> Bool {
        guard !isCapturing else { return true }
        guard hasPermission() else {
            logger.warning("Microphone permission not granted")
            return false
        }

        self.onAudioData = onAudioData

        let inputNode = engine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)

        // Use the hardware's native format; we'll convert to Float samples
        let targetSampleRate: Double = 44100
        guard let monoFormat = AVAudioFormat(
            commonFormat: .pcmFormatFloat32,
            sampleRate: targetSampleRate,
            channels: 1,
            interleaved: false
        ) else {
            logger.error("Failed to create mono audio format")
            return false
        }

        inputNode.installTap(onBus: 0, bufferSize: 1024, format: monoFormat) { [weak self] buffer, _ in
            Task { [weak self] in
                await self?.processBuffer(buffer)
            }
        }

        do {
            try engine.start()
            isCapturing = true
            logger.info("Microphone capture started")
            return true
        } catch {
            logger.error("Failed to start audio engine: \(error.localizedDescription)")
            inputNode.removeTap(onBus: 0)
            return false
        }
    }

    /// Stops audio capture and releases resources.
    func stop() {
        guard isCapturing else { return }
        engine.stop()
        engine.inputNode.removeTap(onBus: 0)
        isCapturing = false
        onAudioData = nil
        logger.info("Microphone capture stopped")
    }

    // MARK: - Private

    private func processBuffer(_ buffer: AVAudioPCMBuffer) {
        guard let channelData = buffer.floatChannelData else { return }
        let frameCount = Int(buffer.frameLength)
        let samples = channelData[0]

        var floatArray = [Float](unsafeUninitializedCapacity: frameCount) { dest, count in
            dest.baseAddress?.initialize(from: samples, count: frameCount)
            count = frameCount
        }

        // Normalize from [-1.0, 1.0] (already Float32 PCM)
        onAudioData?(floatArray)
    }
}
