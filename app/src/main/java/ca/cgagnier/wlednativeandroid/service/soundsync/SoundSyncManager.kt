package ca.cgagnier.wlednativeandroid.service.soundsync

import android.content.Context
import android.net.wifi.WifiManager
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.AudioSource
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.MicrophoneAudioSource
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.GainControl
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val TAG = "SoundSyncManager"
private const val EFFECTIVE_GAIN_UPDATE_EPSILON = 0.05f
private const val MILLIS_PER_SECOND = 1000L
private val logger = Logger.withTag(TAG)

/**
 * Locale-independent error codes for Sound Sync failures, resolved to localized strings in the UI.
 */
enum class SoundSyncError {
    PERMISSION_REQUIRED,
    CAPTURE_FAILED,
}

/**
 * State of the sound sync feature, exposed to the UI.
 */
data class SoundSyncState(
    val isRunning: Boolean = false,
    val gain: Float = 1.0f,
    val effectiveGain: Float = 1.0f,
    val agcMode: AgcMode = AgcMode.OFF,
    val squelch: Int = SoundSyncProcessor.DEFAULT_SQUELCH,
    val error: SoundSyncError? = null,
) {
    val agcEnabled: Boolean
        get() = agcMode != AgcMode.OFF
}

/**
 * Coordinates microphone audio capture, shared KMP DSP processing, and UDP packet sending
 * for the sound sync feature.
 *
 * Manages the lifecycle of [AudioSource], runs [SoundSyncProcessor] on incoming
 * samples, and sends UDP Sound Sync V2 packets at up to 50fps to the multicast group and
 * known device addresses.
 */
@Suppress("LongParameterList")
class SoundSyncManager(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    audioSource: AudioSource? = null,
    private val senderFactory: () -> SoundSyncPacketSender = { UdpSoundSyncSender() },
) {
    private val managerScope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]) + ioDispatcher,
    )

    private val _state = MutableStateFlow(SoundSyncState())
    val state: StateFlow<SoundSyncState> = _state.asStateFlow()

    private val _latestPacket = MutableStateFlow(UdpSoundSyncPacket())
    val latestPacket: StateFlow<UdpSoundSyncPacket> = _latestPacket.asStateFlow()

    private val processor = SoundSyncProcessor()
    private val processorLock = Any()
    private var capturedSamples: Long = 0L
    private val audioSource: AudioSource = audioSource ?: MicrophoneAudioSource(
        context = context,
        coroutineScope = managerScope,
        ioDispatcher = ioDispatcher,
    )

    @Volatile
    private var udpSender: SoundSyncPacketSender? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var deviceListJob: Job? = null
    private var audioMonitorJob: Job? = null

    fun hasRecordAudioPermission(): Boolean = audioSource.hasPermission()

    /**
     * Starts sound sync with the given configuration.
     *
     * @param gain Manual gain factor (0.0 - 30.0).
     * @param agcMode Automatic gain control mode.
     * @param squelch Noise gate threshold (`0..100`).
     * @return `true` if started successfully.
     */
    @Synchronized
    fun start(
        gain: Float,
        agcMode: AgcMode = AgcMode.OFF,
        squelch: Int = SoundSyncProcessor.DEFAULT_SQUELCH,
    ): Boolean {
        if (_state.value.isRunning) {
            stop()
        }

        val clampedGain = gain.coerceIn(0f, GainControl.MAX_MANUAL_GAIN)
        val clampedSquelch = squelch.coerceIn(SoundSyncProcessor.MIN_SQUELCH, SoundSyncProcessor.MAX_SQUELCH)
        _state.update {
            it.copy(
                gain = clampedGain,
                agcMode = agcMode,
                squelch = clampedSquelch,
                error = null,
            )
        }

        if (!audioSource.hasPermission()) {
            _state.update { it.copy(error = SoundSyncError.PERMISSION_REQUIRED) }
            return false
        }

        val initialEffectiveGain = synchronized(processorLock) {
            capturedSamples = 0L
            processor.reset()
            processor.configureSampleRate(audioSource.sampleRate)
            processor.setGain(clampedGain)
            processor.setAgcMode(agcMode)
            processor.setSquelch(clampedSquelch)
            processor.effectiveGain
        }

        acquireMulticastLock()
        udpSender = senderFactory()
        startObservingDevices()
        _state.update {
            it.copy(
                isRunning = true,
                effectiveGain = initialEffectiveGain,
            )
        }

        val started = audioSource.start { samples -> processAudioSamples(samples) }
        if (!started) {
            _state.update {
                it.copy(
                    isRunning = false,
                    error = SoundSyncError.CAPTURE_FAILED,
                )
            }
            cleanup()
            return false
        }

        startMonitoringAudioSource()
        logger.i { "Sound sync started" }
        return true
    }

    /** Stops sound sync and releases active capture/network resources. */
    @Synchronized
    fun stop() {
        if (!_state.value.isRunning) return

        _state.update { it.copy(isRunning = false) }
        _latestPacket.value = UdpSoundSyncPacket()
        audioSource.stop()
        cleanup()
        logger.i { "Sound sync stopped" }
    }

    /** Updates the manual gain in real-time. */
    fun setGain(gain: Float) {
        val clampedGain = gain.coerceIn(0f, GainControl.MAX_MANUAL_GAIN)
        synchronized(processorLock) {
            processor.setGain(clampedGain)
        }
        _state.update { it.copy(gain = clampedGain) }
    }

    /** Updates automatic gain control mode in real-time. */
    fun setAgcMode(mode: AgcMode) {
        val currentEffectiveGain = synchronized(processorLock) {
            processor.setAgcMode(mode)
            processor.effectiveGain
        }
        _state.update {
            it.copy(
                agcMode = mode,
                effectiveGain = currentEffectiveGain,
            )
        }
    }

    /** Updates the squelch noise gate threshold in real-time. */
    fun setSquelch(squelch: Int) {
        val clamped = squelch.coerceIn(SoundSyncProcessor.MIN_SQUELCH, SoundSyncProcessor.MAX_SQUELCH)
        synchronized(processorLock) {
            processor.setSquelch(clamped)
        }
        _state.update { it.copy(squelch = clamped) }
    }

    @Synchronized
    fun destroy() {
        stop()
        audioSource.destroy()
        managerScope.cancel()
    }

    private fun processAudioSamples(samples: FloatArray) {
        val (packet, currentEffectiveGain) = synchronized(processorLock) {
            if (!_state.value.isRunning) return
            val sampleRate = audioSource.sampleRate.coerceAtLeast(1)
            val nowMs = capturedSamples * MILLIS_PER_SECOND / sampleRate
            capturedSamples += samples.size
            val emitted = processor.process(samples, nowMs) ?: return
            emitted to processor.effectiveGain
        }
        udpSender?.sendPacket(packet)
        if (_state.value.isRunning) {
            _latestPacket.value = packet
            if (_state.value.agcEnabled) {
                _state.update { state ->
                    if (state.isRunning && state.agcEnabled &&
                        abs(state.effectiveGain - currentEffectiveGain) >= EFFECTIVE_GAIN_UPDATE_EPSILON
                    ) {
                        state.copy(effectiveGain = currentEffectiveGain)
                    } else {
                        state
                    }
                }
            }
        }
    }

    private fun acquireMulticastLock() {
        releaseMulticastLock()
        runCatching {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.createMulticastLock(TAG)?.apply {
                setReferenceCounted(false)
                acquire()
            }?.let { multicastLock = it }
        }.onFailure { logger.w(it) { "Failed to acquire multicast lock" } }
    }

    private fun releaseMulticastLock() {
        runCatching {
            multicastLock?.let { lock ->
                if (lock.isHeld) lock.release()
            }
        }
        multicastLock = null
    }

    private fun startMonitoringAudioSource() {
        audioMonitorJob?.cancel()
        audioMonitorJob = managerScope.launch {
            audioSource.isRunning.collect { sourceRunning ->
                if (!sourceRunning && _state.value.isRunning) {
                    _state.update { it.copy(error = SoundSyncError.CAPTURE_FAILED) }
                    stop()
                }
            }
        }
    }

    private fun startObservingDevices() {
        deviceListJob?.cancel()
        deviceListJob = managerScope.launch {
            deviceRepository.allDevices.collect { devices ->
                val addresses = devices.filter { !it.isHidden }.map { it.address }
                udpSender?.updateUnicastAddresses(addresses)
            }
        }
    }

    private fun cleanup() {
        audioMonitorJob?.cancel()
        audioMonitorJob = null
        deviceListJob?.cancel()
        deviceListJob = null
        udpSender?.close()
        udpSender = null
        releaseMulticastLock()
    }
}
