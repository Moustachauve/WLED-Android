import AVFAudio
import Foundation
import os
import Shared
import Testing
@testable import WLED

private actor FakeSoundSyncManager: SoundSyncManaging {
    private var state: SoundSyncState
    private let permissionLock: OSAllocatedUnfairLock<AVAudioApplication.recordPermission>
    private let requestPermissionResult: Bool
    private let startResult: Bool
    private var stateContinuation: AsyncStream<SoundSyncState>.Continuation?
    private(set) var startCallCount = 0
    private(set) var lastStartedAddresses: [String] = []

    init(
        initialState: SoundSyncState = SoundSyncState(),
        permission: AVAudioApplication.recordPermission = .granted,
        requestPermissionResult: Bool = true,
        startResult: Bool = true
    ) {
        self.state = initialState
        self.permissionLock = OSAllocatedUnfairLock(initialState: permission)
        self.requestPermissionResult = requestPermissionResult
        self.startResult = startResult
    }

    func getState() -> SoundSyncState {
        state
    }

    func stateStream() -> AsyncStream<SoundSyncState> {
        let (stream, continuation) = AsyncStream.makeStream(
            of: SoundSyncState.self,
            bufferingPolicy: .bufferingNewest(1)
        )
        continuation.yield(state)
        stateContinuation = continuation
        return stream
    }

    nonisolated func recordPermissionStatus() -> AVAudioApplication.recordPermission {
        permissionLock.withLock { $0 }
    }

    func requestRecordAudioPermission() async -> Bool {
        if requestPermissionResult {
            permissionLock.withLock { $0 = .granted }
        }
        return requestPermissionResult
    }

    func start(gain: Float, agcEnabled: Bool, deviceAddresses: [String]) async -> Bool {
        startCallCount += 1
        lastStartedAddresses = deviceAddresses
        state.gain = gain
        state.agcEnabled = agcEnabled
        if startResult {
            state.isRunning = true
            state.errorMessage = nil
        } else {
            state.isRunning = false
            state.errorMessage = "Capture failed"
        }
        stateContinuation?.yield(state)
        return startResult
    }

    func stop() async {
        state.isRunning = false
        state.fftChannels = Array(repeating: 0, count: Int(UdpSoundSyncPacket.Companion.shared.FFT_CHANNEL_COUNT))
        state.sampleLevel = 0
        state.isPeak = false
        stateContinuation?.yield(state)
    }

    func setGain(_ gain: Float) async {
        state.gain = gain
        stateContinuation?.yield(state)
    }

    func setAgcEnabled(_ enabled: Bool) async {
        state.agcEnabled = enabled
        stateContinuation?.yield(state)
    }

    func updateDeviceAddresses(_ addresses: [String]) async {
        lastStartedAddresses = addresses
    }

    func finishStream() {
        stateContinuation?.finish()
    }
}

@MainActor
struct SoundSyncViewModelTests {

    let database: AppDatabase

    init() {
        self.database = AppDatabase(inMemory: true)
    }

    @Test func observeLoadsPersistedGainWhenNotRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncGain(gain: 4.5)
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.gain == 4.5
        }

        #expect(await manager.getState().gain == 4.5)
    }

    @Test func observeLoadsPersistedAgcWhenNotRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: true)
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.agcEnabled
        }

        #expect(await manager.getState().agcEnabled)
    }

    @Test func observePreservesLiveGainWhenAlreadyRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncGain(gain: 1.0)
        let runningState = SoundSyncState(isRunning: true, gain: 7.5, agcEnabled: false)
        let manager = FakeSoundSyncManager(initialState: runningState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning && viewModel.gain == 7.5
        }

        #expect(await manager.getState().gain == 7.5)
    }

    @Test func observePreservesLiveAgcWhenAlreadyRunning() async throws {
        try await database.userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: false)
        let runningState = SoundSyncState(isRunning: true, gain: 1.0, agcEnabled: true)
        let manager = FakeSoundSyncManager(initialState: runningState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning && viewModel.agcEnabled
        }

        #expect(await manager.getState().agcEnabled)
    }

    @Test func startWhenPermissionDeniedOpensSettingsAndDoesNotStart() async {
        var didOpenSettings = false
        let manager = FakeSoundSyncManager(permission: .denied)
        let viewModel = makeViewModel(manager: manager) {
            didOpenSettings = true
        }

        await viewModel.start()

        #expect(didOpenSettings)
        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenPermissionRequestRefusedDoesNotStart() async {
        let manager = FakeSoundSyncManager(
            permission: .undetermined,
            requestPermissionResult: false
        )
        let viewModel = makeViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.isRunning)
        #expect(await manager.startCallCount == 0)
    }

    @Test func startWhenManagerFailsToStartSetsErrorMessageAndKeepsRunningFalse() async {
        let manager = FakeSoundSyncManager(startResult: false)
        let viewModel = makeViewModel(manager: manager)

        await viewModel.start()

        #expect(!viewModel.isRunning)
        #expect(viewModel.errorMessage == "Capture failed")
    }

    @Test func startFiltersOutHiddenDevicesAndUpdatesRunningState() async throws {
        let visibleDevice = Shared.Device(
            macAddress: "aabbccddeeff",
            address: "192.168.1.50",
            isHidden: false
        )
        let hiddenDevice = Shared.Device(
            macAddress: "112233445566",
            address: "192.168.1.51",
            isHidden: true
        )
        try await database.deviceRepository.insert(device: visibleDevice)
        try await database.deviceRepository.insert(device: hiddenDevice)

        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)

        await viewModel.start()

        #expect(viewModel.isRunning)
        #expect(await manager.lastStartedAddresses == ["192.168.1.50"])
        await viewModel.stop()
    }

    @Test(
        arguments: [
            (true, true, Float(5.5)),
            (true, false, Float(2.0)),
            (false, true, Float(2.0))
        ]
    )
    func observeReflectsDisplayedGainForRunningAndAgcStates(
        isRunning: Bool,
        agcEnabled: Bool,
        expectedDisplayedGain: Float
    ) async throws {
        try await database.userPreferencesRepository.updateSoundSyncGain(gain: 2.0)
        try await database.userPreferencesRepository.updateSoundSyncAgcEnabled(enabled: agcEnabled)
        let initialState = SoundSyncState(
            isRunning: isRunning,
            gain: 2.0,
            effectiveGain: 5.5,
            agcEnabled: agcEnabled
        )
        let manager = FakeSoundSyncManager(initialState: initialState)
        let viewModel = makeViewModel(manager: manager)

        try await runObserveUntil(viewModel: viewModel, manager: manager) {
            viewModel.isRunning == isRunning &&
                viewModel.agcEnabled == agcEnabled &&
                viewModel.displayedGain == expectedDisplayedGain
        }

        #expect(viewModel.gain == 2.0)
    }

    @Test func stopResetsRunningState() async {
        let manager = FakeSoundSyncManager()
        let viewModel = makeViewModel(manager: manager)
        await viewModel.start()

        await viewModel.stop()

        #expect(!viewModel.isRunning)
    }

    private func makeViewModel(
        manager: FakeSoundSyncManager,
        openSettings: @escaping @MainActor () -> Void = {}
    ) -> SoundSyncViewModel {
        SoundSyncViewModel(
            soundSyncManager: manager,
            userPreferencesRepository: database.userPreferencesRepository,
            deviceRepository: database.deviceRepository,
            openSettings: openSettings
        )
    }

    private func runObserveUntil(
        viewModel: SoundSyncViewModel,
        manager: FakeSoundSyncManager,
        condition: @escaping @MainActor () -> Bool
    ) async throws {
        let observeTask = Task {
            await viewModel.observe()
        }
        try await waitUntil(condition)
        await manager.finishStream()
        await observeTask.value
    }
}
