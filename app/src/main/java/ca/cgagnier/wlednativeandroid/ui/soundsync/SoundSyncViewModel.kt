package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncError
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncManager
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncService
import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SoundSyncViewModel(
    private val context: Context,
    private val soundSyncManager: SoundSyncManager,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val hasRecordAudioPermission = MutableStateFlow(soundSyncManager.hasRecordAudioPermission())

    init {
        viewModelScope.launch {
            if (!soundSyncManager.state.value.isRunning) {
                soundSyncManager.setGain(preferencesRepository.soundSyncGain.first())
                soundSyncManager.setAgcEnabled(preferencesRepository.soundSyncAgcEnabled.first())
                soundSyncManager.setAgcMode(preferencesRepository.soundSyncAgcMode.first())
                soundSyncManager.setSquelch(preferencesRepository.soundSyncSquelch.first())
            }
        }
    }

    val uiState: StateFlow<SoundSyncUiState> = combine(
        soundSyncManager.state,
        hasRecordAudioPermission,
    ) { state, hasPermission ->
        SoundSyncUiState(
            isRunning = state.isRunning,
            gain = state.gain,
            effectiveGain = state.effectiveGain,
            agcEnabled = state.agcEnabled,
            agcMode = state.agcMode,
            squelch = state.squelch,
            error = state.error,
            hasRecordAudioPermission = hasPermission,
        )
    }.stateIn(
        viewModelScope,
        WhileSubscribed(STATE_FLOW_TIMEOUT_MS),
        soundSyncManager.state.value.let { current ->
            SoundSyncUiState(
                isRunning = current.isRunning,
                gain = current.gain,
                effectiveGain = current.effectiveGain,
                agcEnabled = current.agcEnabled,
                agcMode = current.agcMode,
                squelch = current.squelch,
                error = current.error,
                hasRecordAudioPermission = hasRecordAudioPermission.value,
            )
        },
    )

    val latestPacket: StateFlow<UdpSoundSyncPacket> = soundSyncManager.latestPacket

    fun start() {
        if (!soundSyncManager.hasRecordAudioPermission()) {
            refreshPermissionState()
            return
        }
        val current = soundSyncManager.state.value
        val intent = SoundSyncService.startIntent(
            context = context,
            gain = current.gain,
            agcEnabled = current.agcEnabled,
            agcMode = current.agcMode,
            squelch = current.squelch,
        )
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop() {
        soundSyncManager.stop()
        context.stopService(Intent(context, SoundSyncService::class.java))
    }

    fun setGain(gain: Float) {
        soundSyncManager.setGain(gain)
    }

    fun saveGain() {
        val currentGain = soundSyncManager.state.value.gain
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncGain(currentGain)
        }
    }

    fun setAgcEnabled(enabled: Boolean) {
        soundSyncManager.setAgcEnabled(enabled)
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncAgcEnabled(enabled)
        }
    }

    fun setAgcMode(mode: AgcMode) {
        soundSyncManager.setAgcMode(mode)
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncAgcMode(mode)
        }
    }

    fun setSquelch(squelch: Int) {
        soundSyncManager.setSquelch(squelch)
    }

    fun stepSquelch(delta: Int) {
        val nextSquelch = (soundSyncManager.state.value.squelch + delta)
            .coerceIn(SoundSyncProcessor.MIN_SQUELCH, SoundSyncProcessor.MAX_SQUELCH)
        soundSyncManager.setSquelch(nextSquelch)
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncSquelch(nextSquelch)
        }
    }

    fun saveSquelch() {
        val currentSquelch = soundSyncManager.state.value.squelch
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncSquelch(currentSquelch)
        }
    }

    fun refreshPermissionState() {
        hasRecordAudioPermission.update { soundSyncManager.hasRecordAudioPermission() }
    }

    private companion object {
        /** Timeout for WhileSubscribed in milliseconds. */
        const val STATE_FLOW_TIMEOUT_MS = 5000L
    }
}

data class SoundSyncUiState(
    val isRunning: Boolean = false,
    val gain: Float = 1.0f,
    val effectiveGain: Float = 1.0f,
    val agcEnabled: Boolean = false,
    val agcMode: AgcMode = AgcMode.NORMAL,
    val squelch: Int = SoundSyncProcessor.DEFAULT_SQUELCH,
    val error: SoundSyncError? = null,
    val hasRecordAudioPermission: Boolean = false,
) {
    val displayedGain: Float
        get() = if (isRunning && agcEnabled) effectiveGain else gain
}
