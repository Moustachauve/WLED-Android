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
    var agcEnabled: Bool = false
    var fftChannels: [UInt8] = Array(repeating: 0, count: Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT))
    var sampleLevel: Float = 0
    var isPeak: Bool = false
    var errorMessage: String?
    var recordPermission: AVAudioApplication.recordPermission = .undetermined

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
            agcEnabled: agcEnabled,
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

    /// Toggles AGC and persists the preference.
    func setAgcEnabled(_ enabled: Bool) {
        agcEnabled = enabled
        Task {
            await soundSyncManager.setAgcEnabled(enabled)
            try? await userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: enabled)
        }
    }

    // MARK: - Private

    private func syncInitialPreferences(from initialState: SoundSyncState) async {
        if initialState.isRunning {
            if gain != initialState.gain {
                gain = initialState.gain
            }
            if agcEnabled != initialState.agcEnabled {
                agcEnabled = initialState.agcEnabled
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

        let resolvedAgc = await userPreferencesRepository.soundSyncAgcEnabled.firstValue()?.boolValue
            ?? initialState.agcEnabled
        guard !Task.isCancelled else { return }
        if agcEnabled != resolvedAgc {
            agcEnabled = resolvedAgc
        }
        await soundSyncManager.setAgcEnabled(resolvedAgc)
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
