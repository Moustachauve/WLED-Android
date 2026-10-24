package ca.cgagnier.wlednativeandroid.ui.soundsync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncManager
import ca.cgagnier.wlednativeandroid.service.soundsync.SoundSyncService
import ca.cgagnier.wlednativeandroid.service.soundsync.UdpSoundSyncPacket
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.SoundSyncAudioSourceType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SoundSyncViewModel(
    private val context: Context,
    private val soundSyncManager: SoundSyncManager,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    private val _hasRecordAudioPermission = MutableStateFlow(hasRecordAudioPermission())
    val hasRecordAudioPermission: StateFlow<Boolean> = _hasRecordAudioPermission.asStateFlow()

    val uiState = combine(
        soundSyncManager.state,
        preferencesRepository.soundSyncSourceType,
        preferencesRepository.soundSyncGain,
        preferencesRepository.soundSyncAgcEnabled,
        hasRecordAudioPermission,
    ) { state, savedSourceType, savedGain, savedAgc, hasPermission ->
        SoundSyncUiState(
            isRunning = state.isRunning,
            sourceType = runCatching { SoundSyncAudioSourceType.valueOf(savedSourceType) }
                .getOrDefault(SoundSyncAudioSourceType.MICROPHONE),
            gain = savedGain,
            agcEnabled = savedAgc,
            fftChannels = state.fftChannels,
            sampleLevel = state.sampleLevel,
            isPeak = state.isPeak,
            errorMessage = state.errorMessage,
            hasRecordAudioPermission = hasPermission,
        )
    }.stateIn(viewModelScope, WhileSubscribed(STATE_FLOW_TIMEOUT_MS), SoundSyncUiState())

    fun start() {
        val state = uiState.value
        viewModelScope.launch {
            val intent = SoundSyncService.startIntent(
                context = context,
                sourceType = state.sourceType,
                gain = state.gain,
                agcEnabled = state.agcEnabled,
            )
            ContextCompat.startForegroundService(context, intent)
        }
    }

    fun stop() {
        viewModelScope.launch {
            context.startService(SoundSyncService.stopIntent(context))
        }
    }

    fun setSourceType(sourceType: SoundSyncAudioSourceType) {
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncSourceType(sourceType.name)
            soundSyncManager.setSourceType(sourceType)
        }
    }

    fun setGain(gain: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncGain(gain)
            soundSyncManager.setGain(gain)
        }
    }

    fun setAgcEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateSoundSyncAgcEnabled(enabled)
            soundSyncManager.setAgcEnabled(enabled)
        }
    }

    fun refreshPermissionState() {
        _hasRecordAudioPermission.update { hasRecordAudioPermission() }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private companion object {
        /** Timeout for WhileSubscribed in milliseconds. */
        const val STATE_FLOW_TIMEOUT_MS = 5000L
    }
}

@Stable
data class SoundSyncUiState(
    val isRunning: Boolean = false,
    val sourceType: SoundSyncAudioSourceType = SoundSyncAudioSourceType.MICROPHONE,
    val gain: Float = 1.0f,
    val agcEnabled: Boolean = false,
    val fftChannels: ByteArray = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT),
    val sampleLevel: Float = 0f,
    val isPeak: Boolean = false,
    val errorMessage: String? = null,
    val hasRecordAudioPermission: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoundSyncUiState) return false
        return isRunning == other.isRunning &&
            sourceType == other.sourceType &&
            gain == other.gain &&
            agcEnabled == other.agcEnabled &&
            fftChannels.contentEquals(other.fftChannels) &&
            sampleLevel == other.sampleLevel &&
            isPeak == other.isPeak &&
            errorMessage == other.errorMessage &&
            hasRecordAudioPermission == other.hasRecordAudioPermission
    }

    override fun hashCode(): Int {
        var result = isRunning.hashCode()
        result = 31 * result + sourceType.hashCode()
        result = 31 * result + gain.hashCode()
        result = 31 * result + agcEnabled.hashCode()
        result = 31 * result + fftChannels.contentHashCode()
        result = 31 * result + sampleLevel.hashCode()
        result = 31 * result + isPeak.hashCode()
        result = 31 * result + (errorMessage?.hashCode() ?: 0)
        result = 31 * result + hasRecordAudioPermission.hashCode()
        return result
    }
}
