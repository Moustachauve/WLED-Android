import AVFAudio
import Foundation
import OSLog

private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
    category: "MicrophoneAudioCapture"
)

/// Captures audio from the device microphone using `AVAudioEngine`.
///
/// Emits normalized Float samples in [-1.0, 1.0] at the hardware sample rate, mono.
/// Requires the microphone permission (`NSMicrophoneUsageDescription`).
actor MicrophoneAudioCapture {

    // MARK: - Properties

    /// Requests ~10 ms hardware I/O buffers (hint; CoreAudio typically selects a nearby power-of-two
    /// frame count such as 512 frames / ~10.7 ms at 48 kHz) so `SoundSyncProcessor`'s 20 ms deadline
    /// scheduler receives two sub-frames per packet interval.
    private static let preferredIOBufferDuration: TimeInterval = 0.01

    private var engine: AVAudioEngine?
    private var sinkNode: AVAudioSinkNode?
    private var isCapturing = false
    private var notificationObservers: [any NSObjectProtocol] = []

    // MARK: - Public API

    /// Returns the current microphone record permission status.
    nonisolated func permissionStatus() -> AVAudioApplication.recordPermission {
        AVAudioApplication.shared.recordPermission
    }

    /// Requests microphone permission. Returns `true` if already granted or granted by the user.
    nonisolated func requestPermission() async -> Bool {
        await AVAudioApplication.requestRecordPermission()
    }

    /// Starts audio capture. The `onAudioData` callback is invoked with chunks
    /// of normalized PCM samples, the hardware sample rate in Hz, and the capture sample-clock
    /// timestamp in milliseconds.
    ///
    /// - Parameters:
    ///   - onAudioData: Callback invoked with `(samples, sampleRate, sampleTimeMs)`.
    ///   - onInterruption: Callback invoked when audio capture is interrupted or the route changes.
    /// - Returns: `true` if capture started successfully.
    func start(
        onAudioData: @escaping @Sendable ([Float], Int, Int64) -> Void,
        onInterruption: @escaping @Sendable () -> Void
    ) -> Bool {
        guard !isCapturing else { return true }
        guard permissionStatus() == .granted else {
            logger.warning("Microphone permission not granted")
            return false
        }

        let audioSession = AVAudioSession.sharedInstance()
        do {
            // Use `.defaultToSpeaker` so music playing on the device is not routed to the
            // quiet earpiece receiver, and omit `.allowBluetoothHFP` so connected Bluetooth
            // speakers/headphones remain in high-quality A2DP output mode while capturing
            // from the built-in microphone.
            try audioSession.setCategory(
                .playAndRecord,
                mode: .measurement,
                options: [.mixWithOthers, .defaultToSpeaker]
            )
            try audioSession.setPreferredIOBufferDuration(Self.preferredIOBufferDuration)
            try audioSession.setActive(true)
        } catch {
            logger.error("Failed to configure AVAudioSession: \(error.localizedDescription)")
            return false
        }

        let audioEngine = AVAudioEngine()
        let inputNode = audioEngine.inputNode
        let inputFormat = inputNode.outputFormat(forBus: 0)
        let hardwareSampleRate = Int(inputFormat.sampleRate)
        guard hardwareSampleRate > 0, inputFormat.channelCount > 0 else {
            logger.error("Invalid hardware input format: \(inputFormat.sampleRate) Hz, \(inputFormat.channelCount) ch")
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
            return false
        }

        // Use AVAudioSinkNode rather than installTap, as installTap enforces a 100 ms minimum
        // buffer duration (~10 Hz) on iOS regardless of the requested bufferSize. The receiver
        // block runs on the real-time audio I/O thread; copying the small mono frame slice and
        // yielding into the bounded AsyncStream is accepted for capture-only use.
        let sink = AVAudioSinkNode { timestamp, frameCount, audioBufferList -> OSStatus in
            guard frameCount > 0 else { return noErr }
            let buffers = UnsafeMutableAudioBufferListPointer(UnsafeMutablePointer(mutating: audioBufferList))
            guard let firstBuffer = buffers.first,
                  let dataPtr = firstBuffer.mData?.assumingMemoryBound(to: Float.self) else {
                return noErr
            }
            let samples = Array(UnsafeBufferPointer(start: dataPtr, count: Int(frameCount)))
            let sampleTimeMs = Int64(timestamp.pointee.mSampleTime * 1000.0 / Double(hardwareSampleRate))
            onAudioData(samples, hardwareSampleRate, sampleTimeMs)
            return noErr
        }
        audioEngine.attach(sink)
        audioEngine.connect(inputNode, to: sink, format: inputFormat)

        do {
            try audioEngine.start()
            engine = audioEngine
            sinkNode = sink
            isCapturing = true
            registerNotifications(for: audioEngine, onInterruption: onInterruption)
            logger.info("Microphone capture started at \(hardwareSampleRate) Hz")
            return true
        } catch {
            logger.error("Failed to start audio engine: \(error.localizedDescription)")
            audioEngine.detach(sink)
            try? audioSession.setActive(false, options: .notifyOthersOnDeactivation)
            return false
        }
    }

    /// Stops audio capture and releases resources.
    func stop() {
        guard isCapturing else { return }
        removeNotificationObservers()
        engine?.stop()
        if let sink = sinkNode {
            engine?.detach(sink)
            sinkNode = nil
        }
        engine = nil
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        isCapturing = false
        logger.info("Microphone capture stopped")
    }

    // MARK: - Private

    private func registerNotifications(
        for audioEngine: AVAudioEngine,
        onInterruption: @escaping @Sendable () -> Void
    ) {
        removeNotificationObservers()
        let center = NotificationCenter.default

        let interruptionObserver = center.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: AVAudioSession.sharedInstance(),
            queue: nil
        ) { notification in
            let typeValue = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt
            if typeValue == AVAudioSession.InterruptionType.began.rawValue {
                onInterruption()
            }
        }

        // Stop Sound Sync if the audio hardware route changes (e.g. headphones plugged in),
        // since the input node's hardware sample rate or channel layout may have changed.
        let configObserver = center.addObserver(
            forName: .AVAudioEngineConfigurationChange,
            object: audioEngine,
            queue: nil
        ) { _ in
            onInterruption()
        }

        notificationObservers = [interruptionObserver, configObserver]
    }

    private func removeNotificationObservers() {
        let center = NotificationCenter.default
        for observer in notificationObservers {
            center.removeObserver(observer)
        }
        notificationObservers.removeAll()
    }
}
