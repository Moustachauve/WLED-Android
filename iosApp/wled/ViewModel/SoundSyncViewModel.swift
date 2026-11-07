import AVFAudio
import Foundation
import Observation
import Shared
import UIKit

/// View model for the Sound Sync screen.
///
/// Bridges the `SoundSyncManager` actor to SwiftUI, persists user preferences
/// via the shared `UserPreferencesRepository`, and observes the device list
/// for unicast UDP targets.
@MainActor
@Observable
final class SoundSyncViewModel {

    // MARK: - Observable Properties

    var isRunning: Bool = false
    var gain: Float = 1.0
    var effectiveGain: Float = 1.0
    var agcMode: AgcMode = .off
    var squelch: Int32 = SoundSyncProcessor.Companion.shared.DEFAULT_SQUELCH
    var fftChannels: [UInt8] = Array(repeating: 0, count: Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT))
    var sampleLevel: Float = 0
    var isPeak: Bool = false
    var errorMessage: String?
    var recordPermission: AVAudioApplication.recordPermission = .undetermined

    var agcEnabled: Bool {
        agcMode != .off
    }

    var displayedGain: Float {
        (isRunning && agcEnabled) ? effectiveGain : gain
    }

    var isPermissionDenied: Bool {
        recordPermission == .denied
    }

    // MARK: - Private Properties

    private let soundSyncManager: any SoundSyncManaging
    private let userPreferencesRepository: UserPreferencesRepository
    private let deviceRepository: DeviceRepository
    private let openSettings: @MainActor () -> Void
    @ObservationIgnored private var deviceObservationTask: Task<Void, Never>?
    @ObservationIgnored private var gainUpdateTask: Task<Void, Never>?
    @ObservationIgnored private var squelchUpdateTask: Task<Void, Never>?

    // MARK: - Initialization

    init(
        soundSyncManager: any SoundSyncManaging = SoundSyncManager(),
        userPreferencesRepository: UserPreferencesRepository,
        deviceRepository: DeviceRepository,
        openSettings: @escaping @MainActor () -> Void = SoundSyncViewModel.defaultOpenAppSettings
    ) {
        self.soundSyncManager = soundSyncManager
        self.userPreferencesRepository = userPreferencesRepository
        self.deviceRepository = deviceRepository
        self.openSettings = openSettings
        self.recordPermission = soundSyncManager.recordPermissionStatus()
    }

    // MARK: - Lifecycle

    /// Loads saved preferences, syncs current manager state, and observes live updates.
    func observe() async {
        let initialState = await soundSyncManager.getState()
        guard !Task.isCancelled else { return }
        applyState(initialState)
        await syncInitialPreferences(from: initialState)

        guard !Task.isCancelled else { return }
        let stream = await soundSyncManager.stateStream()
        for await state in stream {
            guard !Task.isCancelled else { break }
            applyState(state)
        }
    }

    // MARK: - Actions

    /// Refreshes the microphone permission state.
    func refreshPermissionState() {
        recordPermission = soundSyncManager.recordPermissionStatus()
    }

    /// Requests microphone permission (or opens Settings if previously denied) and starts sound sync if granted.
    func start() async {
        refreshPermissionState()
        if recordPermission == .denied {
            openSettings()
            return
        }
        if recordPermission != .granted {
            let granted = await soundSyncManager.requestRecordAudioPermission()
            refreshPermissionState()
            guard granted else { return }
        }

        let devices = await deviceRepository.allDevices.firstValue() ?? []
        let addresses = Self.visibleAddresses(from: devices)
        let started = await soundSyncManager.start(
            gain: gain,
            agcMode: agcMode,
            squelch: squelch,
            deviceAddresses: addresses
        )
        let currentState = await soundSyncManager.getState()
        applyState(currentState)
        if started {
            startObservingDevices()
        }
    }

    /// Stops sound sync.
    func stop() async {
        stopObservingDevices()
        await soundSyncManager.stop()
        let currentState = await soundSyncManager.getState()
        applyState(currentState)
    }

    /// Updates the manual gain in real-time while dragging.
    func updateLiveGain(_ newGain: Float) {
        gain = newGain
        gainUpdateTask?.cancel()
        gainUpdateTask = Task { [soundSyncManager] in
            await soundSyncManager.setGain(newGain)
        }
    }

    /// Persists the manual gain preference when slider editing finishes.
    func saveGain() {
        let currentGain = gain
        gainUpdateTask?.cancel()
        gainUpdateTask = Task { [soundSyncManager, userPreferencesRepository] in
            await soundSyncManager.setGain(currentGain)
            try? await userPreferencesRepository.updateSoundSyncGain(gain: currentGain)
        }
    }

    /// Updates the AGC mode and persists the preference.
    func setAgcMode(_ mode: AgcMode) {
        agcMode = mode
        Task {
            await soundSyncManager.setAgcMode(mode)
            try? await userPreferencesRepository.updateSoundSyncAgcMode(mode: mode)
        }
    }

    /// Updates the squelch threshold in real-time while dragging.
    func updateLiveSquelch(_ newSquelch: Int32) {
        let clamped = clampSquelch(newSquelch)
        squelch = clamped
        squelchUpdateTask?.cancel()
        squelchUpdateTask = Task { [soundSyncManager] in
            await soundSyncManager.setSquelch(clamped)
        }
    }

    /// Persists the squelch preference when slider editing finishes.
    func saveSquelch() {
        let currentSquelch = squelch
        squelchUpdateTask?.cancel()
        squelchUpdateTask = Task { [soundSyncManager, userPreferencesRepository] in
            await soundSyncManager.setSquelch(currentSquelch)
            try? await userPreferencesRepository.updateSoundSyncSquelch(squelch: currentSquelch)
        }
    }

    /// Updates the squelch threshold by a discrete step and immediately persists the preference.
    func setSquelch(_ newSquelch: Int32) {
        squelch = clampSquelch(newSquelch)
        saveSquelch()
    }

    // MARK: - Private

    private func clampSquelch(_ value: Int32) -> Int32 {
        min(
            max(value, SoundSyncProcessor.Companion.shared.MIN_SQUELCH),
            SoundSyncProcessor.Companion.shared.MAX_SQUELCH
        )
    }

    private func syncInitialPreferences(from initialState: SoundSyncState) async {
        if initialState.isRunning {
            if gain != initialState.gain {
                gain = initialState.gain
            }
            if agcMode != initialState.agcMode {
                agcMode = initialState.agcMode
            }
            if squelch != initialState.squelch {
                squelch = initialState.squelch
            }
            return
        }

        let resolvedGain = await userPreferencesRepository.soundSyncGain.firstValue()?.floatValue
            ?? initialState.gain
        guard !Task.isCancelled else { return }
        if gain != resolvedGain {
            gain = resolvedGain
        }
        await soundSyncManager.setGain(resolvedGain)

        let resolvedAgcMode = await userPreferencesRepository.soundSyncAgcMode.firstValue()
            ?? initialState.agcMode
        guard !Task.isCancelled else { return }
        if agcMode != resolvedAgcMode {
            agcMode = resolvedAgcMode
        }
        await soundSyncManager.setAgcMode(resolvedAgcMode)

        let resolvedSquelch = await userPreferencesRepository.soundSyncSquelch.firstValue()?.int32Value
            ?? initialState.squelch
        guard !Task.isCancelled else { return }
        if squelch != resolvedSquelch {
            squelch = resolvedSquelch
        }
        await soundSyncManager.setSquelch(resolvedSquelch)
    }

    private func applyState(_ state: SoundSyncState) {
        if isRunning != state.isRunning {
            isRunning = state.isRunning
        }
        if effectiveGain != state.effectiveGain {
            effectiveGain = state.effectiveGain
        }
        if fftChannels != state.fftChannels {
            fftChannels = state.fftChannels
        }
        if sampleLevel != state.sampleLevel {
            sampleLevel = state.sampleLevel
        }
        if isPeak != state.isPeak {
            isPeak = state.isPeak
        }
        if errorMessage != state.errorMessage {
            errorMessage = state.errorMessage
        }
    }

    private func startObservingDevices() {
        deviceObservationTask?.cancel()
        deviceObservationTask = Task { [weak self] in
            guard let self else { return }
            for await devices in self.deviceRepository.allDevices {
                guard !Task.isCancelled else { break }
                await self.soundSyncManager.updateDeviceAddresses(Self.visibleAddresses(from: devices))
            }
        }
    }

    private func stopObservingDevices() {
        deviceObservationTask?.cancel()
        deviceObservationTask = nil
    }

    private static func defaultOpenAppSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }

    private static func visibleAddresses(from devices: [Shared.Device]) -> [String] {
        devices
            .filter { !$0.isHidden }
            .map { $0.address }
    }
}
