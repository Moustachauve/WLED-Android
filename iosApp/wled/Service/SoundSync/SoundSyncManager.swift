import AVFAudio
import Foundation
import OSLog
import Shared

private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
    category: "SoundSyncManager"
)

/// State of the sound sync feature, exposed to the UI.
struct SoundSyncState: Equatable, Sendable {
    var isRunning: Bool = false
    var gain: Float = 1.0
    var agcEnabled: Bool = false
    var fftChannels: [UInt8] = Array(repeating: 0, count: Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT))
    var sampleLevel: Float = 0
    var isPeak: Bool = false
    var errorMessage: String?
}

/// Protocol abstracting `SoundSyncManager` for dependency injection and testing.
protocol SoundSyncManaging: Sendable {
    func getState() async -> SoundSyncState
    func stateStream() async -> AsyncStream<SoundSyncState>
    func recordPermissionStatus() -> AVAudioApplication.recordPermission
    func requestRecordAudioPermission() async -> Bool
    func start(gain: Float, agcEnabled: Bool, deviceAddresses: [String]) async -> Bool
    func stop() async
    func setGain(_ gain: Float) async
    func setAgcEnabled(_ enabled: Bool) async
    func updateDeviceAddresses(_ addresses: [String]) async
}

/// Coordinates microphone audio capture, shared KMP DSP processing, and UDP packet sending
/// for the sound sync feature on iOS.
actor SoundSyncManager: SoundSyncManaging {

    // MARK: - Properties

    private let audioCapture = MicrophoneAudioCapture()
    private let processor = SoundSyncProcessor()
    private var udpSender: UdpSoundSyncSender?
    private var captureProcessingTask: Task<Void, Never>?
    private var audioBufferContinuation: AsyncStream<([Float], Int)>.Continuation?
    private var isStarting = false
    private var lifecycleGeneration: UInt64 = 0

    // State
    private var _state = SoundSyncState()
    private var stateContinuation: AsyncStream<SoundSyncState>.Continuation?

    // MARK: - Initialization

    init() {}

    deinit {
        captureProcessingTask?.cancel()
        audioBufferContinuation?.finish()
        stateContinuation?.finish()
    }

    // MARK: - Public API

    /// Returns the current state.
    func getState() -> SoundSyncState {
        _state
    }

    /// Creates a new stream of state updates for a subscriber, immediately yielding the current state.
    func stateStream() -> AsyncStream<SoundSyncState> {
        stateContinuation?.finish()
        let (stream, continuation) = AsyncStream.makeStream(
            of: SoundSyncState.self,
            bufferingPolicy: .bufferingNewest(1)
        )
        continuation.yield(_state)
        stateContinuation = continuation
        return stream
    }

    /// Returns the current microphone record permission status.
    nonisolated func recordPermissionStatus() -> AVAudioApplication.recordPermission {
        audioCapture.permissionStatus()
    }

    /// Requests microphone permission.
    nonisolated func requestRecordAudioPermission() async -> Bool {
        await audioCapture.requestPermission()
    }

    /// Starts sound sync with the given configuration.
    ///
    /// - Parameters:
    ///   - gain: Manual gain factor (0.0 - 10.0).
    ///   - agcEnabled: Whether automatic gain control is enabled.
    ///   - deviceAddresses: List of device IP addresses for unicast sending.
    /// - Returns: `true` if started successfully.
    func start(gain: Float, agcEnabled: Bool, deviceAddresses: [String]) async -> Bool {
        if _state.isRunning || isStarting {
            await stop()
        }

        lifecycleGeneration &+= 1
        let startGeneration = lifecycleGeneration
        isStarting = true

        let clampedGain = min(max(gain, 0), GainControl.Companion.shared.MAX_MANUAL_GAIN)
        updateState {
            $0.gain = clampedGain
            $0.agcEnabled = agcEnabled
            $0.errorMessage = nil
        }

        processor.reset()
        processor.setGain(gain: clampedGain)
        processor.setAgcEnabled(enabled: agcEnabled)

        let sender = UdpSoundSyncSender()
        await sender.updateUnicastAddresses(deviceAddresses)

        guard isStarting, lifecycleGeneration == startGeneration else {
            await sender.close()
            return false
        }
        udpSender = sender

        let (audioStream, audioContinuation) = AsyncStream.makeStream(
            of: ([Float], Int).self,
            bufferingPolicy: .bufferingNewest(4)
        )
        audioBufferContinuation = audioContinuation

        let started = await audioCapture.start(
            onAudioData: { samples, sampleRate in
                audioContinuation.yield((samples, sampleRate))
            },
            onInterruption: { [weak self] in
                Task { [weak self] in
                    await self?.handleAudioInterruption()
                }
            }
        )

        guard isStarting, lifecycleGeneration == startGeneration else {
            audioContinuation.finish()
            if started {
                await audioCapture.stop()
            }
            await sender.close()
            return false
        }

        isStarting = false

        guard started else {
            audioContinuation.finish()
            audioBufferContinuation = nil
            updateState { $0.errorMessage = String(localized: "Failed to start audio capture.") }
            udpSender = nil
            await sender.close()
            return false
        }

        updateState { $0.isRunning = true }
        captureProcessingTask = Task { [weak self] in
            for await (samples, sampleRate) in audioStream {
                guard !Task.isCancelled else { break }
                await self?.processAudioSamples(samples, sampleRate: sampleRate)
            }
        }
        logger.info("Sound sync started")
        return true
    }

    /// Stops sound sync and releases all resources.
    func stop() async {
        guard _state.isRunning || isStarting else { return }

        lifecycleGeneration &+= 1
        isStarting = false

        captureProcessingTask?.cancel()
        captureProcessingTask = nil
        audioBufferContinuation?.finish()
        audioBufferContinuation = nil

        let sender = udpSender
        udpSender = nil

        let channelCount = Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT)
        updateState {
            $0.isRunning = false
            $0.fftChannels = Array(repeating: 0, count: channelCount)
            $0.sampleLevel = 0
            $0.isPeak = false
        }

        await audioCapture.stop()
        await sender?.close()
        logger.info("Sound sync stopped")
    }

    /// Updates the manual gain in real-time.
    func setGain(_ gain: Float) {
        let clampedGain = min(max(gain, 0), GainControl.Companion.shared.MAX_MANUAL_GAIN)
        processor.setGain(gain: clampedGain)
        updateState { $0.gain = clampedGain }
    }

    /// Toggles automatic gain control in real-time.
    func setAgcEnabled(_ enabled: Bool) {
        processor.setAgcEnabled(enabled: enabled)
        updateState { $0.agcEnabled = enabled }
    }

    /// Updates the list of device addresses for unicast sending.
    func updateDeviceAddresses(_ addresses: [String]) async {
        await udpSender?.updateUnicastAddresses(addresses)
    }

    // MARK: - Private

    private func handleAudioInterruption() async {
        guard _state.isRunning || isStarting else { return }
        logger.warning("Audio session interrupted or route changed; stopping Sound Sync")
        updateState { $0.errorMessage = String(localized: "Audio capture was interrupted.") }
        await stop()
    }

    private func processAudioSamples(_ samples: [Float], sampleRate: Int) async {
        guard _state.isRunning, !samples.isEmpty else { return }

        processor.configureSampleRate(sampleRate: Int32(sampleRate))

        let kotlinSamples = KotlinFloatArray(size: Int32(samples.count))
        for (index, value) in samples.enumerated() {
            kotlinSamples.set(index: Int32(index), value: value)
        }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        guard let packet = processor.process(samples: kotlinSamples, nowMs: nowMs) else { return }

        let fftChannels = packet.fftResult.toUInt8Array()
        updateState {
            $0.fftChannels = fftChannels
            $0.sampleLevel = packet.sampleSmth
            $0.isPeak = packet.samplePeak
        }

        let packetData = packet.toByteArray().toData()
        await udpSender?.sendPacket(packetData: packetData)
    }

    private func updateState(_ update: (inout SoundSyncState) -> Void) {
        update(&_state)
        stateContinuation?.yield(_state)
    }
}

private extension KotlinByteArray {
    func toUInt8Array() -> [UInt8] {
        (0..<size).map { index in
            UInt8(bitPattern: get(index: index))
        }
    }

    func toData() -> Data {
        Data(toUInt8Array())
    }
}
