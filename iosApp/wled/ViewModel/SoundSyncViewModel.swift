import Foundation
import Observation
import OSLog
import Shared

private let logger = Logger(
    subsystem: Bundle.main.bundleIdentifier ?? "ca.cgagnier.wled-native",
    category: "SoundSyncViewModel"
)

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
    var agcEnabled: Bool = false
    var fftChannels: [UInt8] = Array(repeating: 0, count: 16)
    var sampleLevel: Float = 0
    var isPeak: Bool = false
    var errorMessage: String?
    var hasRecordAudioPermission: Bool = false

    // MARK: - Private Properties

    private let soundSyncManager: SoundSyncManager
    private let userPreferencesRepository: UserPreferencesRepository
    private let deviceRepository: DeviceRepository
    @ObservationIgnored private var stateObserverTask: Task<Void, Never>?
    @ObservationIgnored private var deviceObserverTask: Task<Void, Never>?
    @ObservationIgnored private var isSyncingPreferences = false

    // MARK: - Initialization

    init(
        soundSyncManager: SoundSyncManager = SoundSyncManager(),
        userPreferencesRepository: UserPreferencesRepository,
        deviceRepository: DeviceRepository
    ) {
        self.soundSyncManager = soundSyncManager
        self.userPreferencesRepository = userPreferencesRepository
        self.deviceRepository = deviceRepository
        self.hasRecordAudioPermission = soundSyncManager.hasRecordAudioPermission()
    }

    // MARK: - Lifecycle

    /// Starts observing the sound sync manager state and device list.
    func observe() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.observeSoundSyncState() }
            group.addTask { await self.observeGainPreference() }
            group.addTask { await self.observeAgcPreference() }
            group.addTask { await self.observeDevices() }
        }
    }

    private func observeSoundSyncState() async {
        for await state in soundSyncManager.stateStream {
            guard !Task.isCancelled else { break }
            isRunning = state.isRunning
            gain = state.gain
            agcEnabled = state.agcEnabled
            fftChannels = state.fftChannels
            sampleLevel = state.sampleLevel
            isPeak = state.isPeak
            errorMessage = state.errorMessage
        }
    }

    private func observeGainPreference() async {
        for await value in userPreferencesRepository.soundSyncGain {
            guard !Task.isCancelled else { break }
            isSyncingPreferences = true
            gain = value.floatValue
            isSyncingPreferences = false
        }
    }

    private func observeAgcPreference() async {
        for await value in userPreferencesRepository.soundSyncAgcEnabled {
            guard !Task.isCancelled else { break }
            isSyncingPreferences = true
            agcEnabled = value.boolValue
            isSyncingPreferences = false
        }
    }

    private func observeDevices() async {
        for await devices in deviceRepository.allDevices {
            guard !Task.isCancelled else { break }
            let addresses = devices
                .filter { !$0.isHidden.boolValue }
                .map { $0.address }
            await soundSyncManager.updateDeviceAddresses(addresses)
        }
    }

    // MARK: - Actions

    /// Refreshes the microphone permission state.
    func refreshPermissionState() {
        hasRecordAudioPermission = soundSyncManager.hasRecordAudioPermission()
    }

    /// Requests microphone permission and starts sound sync if granted.
    func start() async {
        if !hasRecordAudioPermission {
            let granted = await soundSyncManager.requestRecordAudioPermission()
            hasRecordAudioPermission = granted
            guard granted else { return }
        }

        let addresses = await getDeviceAddresses()
        let started = await soundSyncManager.start(
            gain: gain,
            agcEnabled: agcEnabled,
            deviceAddresses: addresses
        )
        if !started {
            errorMessage = String(localized: "Failed to start audio capture.")
        }
    }

    /// Stops sound sync.
    func stop() {
        Task {
            await soundSyncManager.stop()
        }
    }

    /// Updates the manual gain and persists the preference.
    func setGain(_ newGain: Float) {
        guard !isSyncingPreferences else { return }
        gain = newGain
        Task {
            await soundSyncManager.setGain(newGain)
            try? await userPreferencesRepository.updateSoundSyncGain(gain: newGain)
        }
    }

    /// Toggles AGC and persists the preference.
    func setAgcEnabled(_ enabled: Bool) {
        guard !isSyncingPreferences else { return }
        agcEnabled = enabled
        Task {
            await soundSyncManager.setAgcEnabled(enabled)
            try? await userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: enabled)
        }
    }

    // MARK: - Private

    private func getDeviceAddresses() async -> [String] {
        var addresses: [String] = []
        for await devices in deviceRepository.allDevices {
            addresses = devices
                .filter { !$0.isHidden.boolValue }
                .map { $0.address }
            break
        }
        return addresses
    }
}
