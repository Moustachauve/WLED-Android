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
    var fftChannels: [UInt8] = Array(repeating: 0, count: 16)
    var sampleLevel: Float = 0
    var isPeak: Bool = false
    var errorMessage: String?
}

/// Coordinates audio capture, DSP processing, and UDP packet sending for
/// the sound sync feature on iOS.
///
/// Uses the shared KMP DSP pipeline (`LowCutFilter`, `GainControl`, `FftAnalyzer`,
/// `BeatDetector`) and the shared `UdpSoundSyncPacket` format.
actor SoundSyncManager {

    // MARK: - Constants

    private static let fftWindowSize = 512
    private static let sampleMaxValue: Float = 255
    private static let packetIntervalMs: Int64 = 20 // 50 fps

    // MARK: - Properties

    private let audioCapture = MicrophoneAudioCapture()
    private var udpSender: UdpSoundSyncSender?

    // DSP pipeline (from shared KMP module)
    private let lowCutFilter = LowCutFilter()
    private let gainControl = GainControl()
    private let fftAnalyzer = FftAnalyzer()
    private let beatDetector = BeatDetector()

    // State
    private var _state = SoundSyncState()
    private var stateContinuation: AsyncStream<SoundSyncState>.Continuation?
    nonisolated let stateStream: AsyncStream<SoundSyncState>

    // Sample accumulation buffer for FFT windowing
    private var sampleBuffer = [Float](repeating: 0, count: fftWindowSize)
    private var sampleBufferIndex = 0
    private var sampleBufferFilled = false

    // Packet sending throttle
    private var lastPacketTime: Int64 = 0

    // MARK: - Initialization

    init() {
        var continuation: AsyncStream<SoundSyncState>.Continuation?
        self.stateStream = AsyncStream { cont in
            continuation = cont
        }
        self.stateContinuation = continuation
    }

    deinit {
        stateContinuation?.finish()
    }

    // MARK: - Public API

    /// Returns the current state.
    func getState() -> SoundSyncState {
        _state
    }

    /// Checks whether the microphone permission has been granted.
    nonisolated func hasRecordAudioPermission() -> Bool {
        audioCapture.hasPermission()
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
        if _state.isRunning {
            await stop()
        }

        updateState {
            $0.gain = gain
            $0.agcEnabled = agcEnabled
            $0.errorMessage = nil
        }

        // Reset DSP pipeline
        lowCutFilter.reset()
        gainControl.reset()
        gainControl.setManualGain(gain: gain)
        gainControl.setAgcEnabled(enabled: agcEnabled)
        beatDetector.reset()
        resetSampleBuffer()

        // Create UDP sender
        let sender = UdpSoundSyncSender()
        await sender.updateUnicastAddresses(deviceAddresses)
        udpSender = sender

        // Start audio capture
        let started = await audioCapture.start { [weak self] samples in
            Task { [weak self] in
                await self?.processAudioSamples(samples)
            }
        }

        guard started else {
            updateState { $0.errorMessage = String(localized: "Failed to start audio capture.") }
            await udpSender?.close()
            udpSender = nil
            return false
        }

        updateState { $0.isRunning = true }
        logger.info("Sound sync started")
        return true
    }

    /// Stops sound sync and releases all resources.
    func stop() async {
        guard _state.isRunning else { return }

        await audioCapture.stop()
        await udpSender?.close()
        udpSender = nil

        updateState {
            $0.isRunning = false
            $0.fftChannels = Array(repeating: 0, count: 16)
            $0.sampleLevel = 0
            $0.isPeak = false
        }
        logger.info("Sound sync stopped")
    }

    /// Updates the manual gain in real-time.
    func setGain(_ gain: Float) {
        gainControl.setManualGain(gain: gain)
        updateState { $0.gain = gain }
    }

    /// Toggles automatic gain control in real-time.
    func setAgcEnabled(_ enabled: Bool) {
        gainControl.setAgcEnabled(enabled: enabled)
        updateState { $0.agcEnabled = enabled }
    }

    /// Updates the list of device addresses for unicast sending.
    func updateDeviceAddresses(_ addresses: [String]) async {
        await udpSender?.updateUnicastAddresses(addresses)
    }

    // MARK: - Private

    private func processAudioSamples(_ samples: [Float]) async {
        guard !samples.isEmpty else { return }

        // Throttle to max 50fps
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        if now - lastPacketTime < Self.packetIntervalMs {
            accumulateSamples(samples)
            return
        }
        lastPacketTime = now

        // Run DSP pipeline (shared KMP classes)
        let filtered = lowCutFilter.process(samples: samples)
        let gained = gainControl.process(samples: filtered)

        // Accumulate for FFT window
        accumulateSamples(gained)

        // Compute sample values
        let sampleRaw = computePeak(gained)
        let sampleSmth = computeAverage(gained)
        let samplePeak = beatDetector.detect(sampleAmplitude: sampleRaw)

        // Run FFT on the accumulated window
        let fftWindow = getFftWindow()
        let fftResult = fftAnalyzer.analyze(samples: fftWindow)

        // Convert FFT channels from [Int8] to [UInt8]
        let fftChannels = fftResult.fftChannels.map { UInt8(bitPattern: $0) }

        // Build packet (shared KMP class)
        let packet = UdpSoundSyncPacket(
            sampleRaw: min(max(sampleRaw, 0), Self.sampleMaxValue),
            sampleSmth: min(max(sampleSmth, 0), Self.sampleMaxValue),
            samplePeak: samplePeak,
            frameCounter: 0, // Will be overridden by sender
            fftResult: fftResult.fftChannels,
            fftMagnitude: fftResult.magnitude,
            fftMajorPeak: fftResult.majorPeak
        )

        // Serialize and send
        let packetBytes = packet.toByteArray()
        let data = Data(packetBytes.map { UInt8(bitPattern: $0) })
        await udpSender?.sendPacket(packetData: data)

        // Update UI state
        updateState {
            $0.fftChannels = fftChannels
            $0.sampleLevel = sampleSmth
            $0.isPeak = samplePeak
        }
    }

    private func accumulateSamples(_ samples: [Float]) {
        for sample in samples {
            sampleBuffer[sampleBufferIndex] = sample
            sampleBufferIndex = (sampleBufferIndex + 1) % Self.fftWindowSize
            if sampleBufferIndex == 0 { sampleBufferFilled = true }
        }
    }

    private func getFftWindow() -> [Float] {
        guard sampleBufferFilled || sampleBufferIndex > 0 else {
            return [Float](repeating: 0, count: Self.fftWindowSize)
        }
        let start = sampleBufferFilled ? sampleBufferIndex : 0
        var window = [Float](repeating: 0, count: Self.fftWindowSize)
        for i in 0..<Self.fftWindowSize {
            window[i] = sampleBuffer[(start + i) % Self.fftWindowSize]
        }
        return window
    }

    private func resetSampleBuffer() {
        sampleBuffer = [Float](repeating: 0, count: Self.fftWindowSize)
        sampleBufferIndex = 0
        sampleBufferFilled = false
    }

    private func computePeak(_ samples: [Float]) -> Float {
        var peak: Float = 0
        for sample in samples {
            let absValue = abs(sample)
            if absValue > peak { peak = absValue }
        }
        return peak * Self.sampleMaxValue
    }

    private func computeAverage(_ samples: [Float]) -> Float {
        guard !samples.isEmpty else { return 0 }
        var sum: Float = 0
        for sample in samples {
            sum += abs(sample)
        }
        return sum / Float(samples.count) * Self.sampleMaxValue
    }

    private func updateState(_ update: (inout SoundSyncState) -> Void) {
        update(&_state)
        stateContinuation?.yield(_state)
    }
}
