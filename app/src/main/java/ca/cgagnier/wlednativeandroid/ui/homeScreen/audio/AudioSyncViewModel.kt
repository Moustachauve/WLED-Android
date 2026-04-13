package ca.cgagnier.wlednativeandroid.ui.homeScreen.audio

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.service.audio.AudioCaptureService
import ca.cgagnier.wlednativeandroid.service.audio.AudioData
import ca.cgagnier.wlednativeandroid.service.audio.UdpSyncSender
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AudioSyncViewModel @Inject constructor(
    application: Application,
) : AndroidViewModel(application) {

    private var service: AudioCaptureService? = null
    private val _isBound = MutableStateFlow(false)

    private val _targetAddress = MutableStateFlow(UdpSyncSender.DEFAULT_MULTICAST_ADDRESS)
    val targetAddress: StateFlow<String> = _targetAddress.asStateFlow()

    private val _targetPort = MutableStateFlow(UdpSyncSender.DEFAULT_PORT)
    val targetPort: StateFlow<Int> = _targetPort.asStateFlow()

    private val _gain = MutableStateFlow(1.0f)
    val gain: StateFlow<Float> = _gain.asStateFlow()

    val isRunning: StateFlow<Boolean> = _isBound
        .flatMapLatest { bound ->
            if (bound) service?.isRunning ?: flowOf(false) else flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val currentAudioData: StateFlow<AudioData?> = _isBound
        .flatMapLatest { bound ->
            if (bound) service?.currentAudioData ?: flowOf(null) else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val audioBinder = binder as AudioCaptureService.AudioSyncBinder
            service = audioBinder.getService()
            _isBound.value = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            _isBound.value = false
        }
    }

    fun bindService() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService() {
        if (_isBound.value) {
            try {
                getApplication<Application>().unbindService(connection)
            } catch (_: IllegalArgumentException) {
                // Already unbound
            }
            _isBound.value = false
            service = null
        }
    }

    fun startAudioSync() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_START
            putExtra(AudioCaptureService.EXTRA_TARGET_ADDRESS, _targetAddress.value)
            putExtra(AudioCaptureService.EXTRA_TARGET_PORT, _targetPort.value)
            putExtra(AudioCaptureService.EXTRA_GAIN, _gain.value)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun stopAudioSync() {
        val context = getApplication<Application>()
        val intent = Intent(context, AudioCaptureService::class.java).apply {
            action = AudioCaptureService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun updateTargetAddress(address: String) {
        _targetAddress.update { address }
    }

    fun updateTargetPort(port: Int) {
        _targetPort.update { port }
    }

    fun updateGain(newGain: Float) {
        _gain.update { newGain }
        service?.updateGain(newGain)
    }

    override fun onCleared() {
        unbindService()
        super.onCleared()
    }
}
