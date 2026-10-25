package ca.cgagnier.wlednativeandroid.service.soundsync

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.core.content.ContextCompat
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.AudioSource
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.MicrophoneAudioSource
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.MusicAudioSource
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.SoundSyncAudioSourceType
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.BeatDetector
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.FftAnalyzer
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.GainControl
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.LowCutFilter
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
import kotlinx.coroutines.withContext
import kotlin.math.abs

private const val TAG = "SoundSyncManager"
private val logger = Logger.withTag(TAG)

/**
 * State of the sound sync feature, exposed to the UI.
 */
data class SoundSyncState(
    val isRunning: Boolean = false,
    val sourceType: SoundSyncAudioSourceType = SoundSyncAudioSourceType.MICROPHONE,
    val gain: Float = 1.0f,
    val agcEnabled: Boolean = false,
    val fftChannels: ByteArray = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT),
    val sampleLevel: Float = 0f,
    val isPeak: Boolean = false,
    val errorMessage: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SoundSyncState) return false
        return isRunning == other.isRunning &&
            sourceType == other.sourceType &&
            gain == other.gain &&
            agcEnabled == other.agcEnabled &&
            fftChannels.contentEquals(other.fftChannels) &&
            sampleLevel == other.sampleLevel &&
            isPeak == other.isPeak &&
            errorMessage == other.errorMessage
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
        return result
    }
}

/**
 * Coordinates audio capture, DSP processing, and UDP packet sending for
 * the sound sync feature.
 *
 * Manages the lifecycle of the audio source, runs the DSP pipeline on
 * incoming samples, and sends UDP Sound Sync V2 packets at up to 50fps
 * to the multicast group and known device addresses.
 */
class SoundSyncManager(
    private val context: Context,
    private val deviceRepository: DeviceRepository,
    coroutineScope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
) {
    private val managerScope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]) + ioDispatcher,
    )

    private val _state = MutableStateFlow(SoundSyncState())
    val state: StateFlow<SoundSyncState> = _state.asStateFlow()

    // DSP pipeline
    private val lowCutFilter = LowCutFilter()
    private val gainControl = GainControl()
    private val fftAnalyzer = FftAnalyzer()
    private val beatDetector = BeatDetector()

    // UDP sender
    private var udpSender: UdpSoundSyncSender? = null

    // Audio source
    private var audioSource: AudioSource? = null

    // Multicast lock
    private var multicastLock: WifiManager.MulticastLock? = null

    // Device list observation
    private var deviceListJob: Job? = null

    // Packet sending throttle
    private var lastPacketTime = 0L

    // Sample accumulation buffer for FFT windowing
    private val sampleBuffer = FloatArray(FFT_WINDOW_SIZE)
    private var sampleBufferIndex = 0
    private var sampleBufferFilled = false

    /**
     * Starts sound sync with the given configuration.
     *
     * @param sourceType Audio source (microphone or music).
     * @param gain Manual gain factor (0.0 - 10.0).
     * @param agcEnabled Whether automatic gain control is enabled.
     * @return `true` if started successfully.
     */
    fun start(sourceType: SoundSyncAudioSourceType, gain: Float, agcEnabled: Boolean): Boolean {
        if (_state.value.isRunning) {
            stop()
        }

        _state.update {
            it.copy(
                sourceType = sourceType,
                gain = gain,
                agcEnabled = agcEnabled,
                errorMessage = null,
            )
        }

        // Reset DSP pipeline
        lowCutFilter.reset()
        gainControl.reset()
        gainControl.setManualGain(gain)
        gainControl.setAgcEnabled(agcEnabled)
        beatDetector.reset()
        resetSampleBuffer()

        // Acquire multicast lock for reliable multicast sending
        acquireMulticastLock()

        // Create UDP sender
        udpSender = UdpSoundSyncSender()

        // Start observing device list for unicast addresses
        startObservingDevices()

        // Check permission before attempting to start
        if (!hasRecordAudioPermission()) {
            _state.update { it.copy(errorMessage = context.getString(R.string.sound_sync_error_permission)) }
            cleanup()
            return false
        }

        // Create and start audio source
        val source = createAudioSource(sourceType)
        if (source == null) {
            _state.update { it.copy(errorMessage = context.getString(R.string.sound_sync_error_create_source)) }
            cleanup()
            return false
        }

        val started = source.start { samples ->
            processAudioSamples(samples)
        }
        if (!started) {
            _state.update { it.copy(errorMessage = context.getString(R.string.sound_sync_error_capture)) }
            cleanup()
            return false
        }

        audioSource = source
        _state.update { it.copy(isRunning = true) }
        logger.i { "Sound sync started with source: $sourceType" }
        return true
    }

    /** Stops sound sync and releases all resources. */
    fun stop() {
        if (!_state.value.isRunning) return

        audioSource?.stop()
        audioSource = null

        cleanup()

        _state.update {
            it.copy(
                isRunning = false,
                fftChannels = ByteArray(UdpSoundSyncPacket.FFT_CHANNEL_COUNT),
                sampleLevel = 0f,
                isPeak = false,
            )
        }
        logger.i { "Sound sync stopped" }
    }

    /** Updates the manual gain in real-time. */
    fun setGain(gain: Float) {
        gainControl.setManualGain(gain)
        _state.update { it.copy(gain = gain.coerceIn(0f, GainControl.MAX_MANUAL_GAIN)) }
    }

    /** Toggles automatic gain control in real-time. */
    fun setAgcEnabled(enabled: Boolean) {
        gainControl.setAgcEnabled(enabled)
        _state.update { it.copy(agcEnabled = enabled) }
    }

    /** Switches the audio source type. Restarts sound sync if running. */
    fun setSourceType(sourceType: SoundSyncAudioSourceType) {
        if (_state.value.sourceType == sourceType) return
        val wasRunning = _state.value.isRunning
        val gain = _state.value.gain
        val agcEnabled = _state.value.agcEnabled
        if (wasRunning) {
            stop()
            start(sourceType, gain, agcEnabled)
        } else {
            _state.update { it.copy(sourceType = sourceType) }
        }
    }

    fun destroy() {
        stop()
        managerScope.cancel()
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun createAudioSource(type: SoundSyncAudioSourceType): AudioSource? = when (type) {
        SoundSyncAudioSourceType.MICROPHONE -> MicrophoneAudioSource(context)
        SoundSyncAudioSourceType.MUSIC -> MusicAudioSource(context)
    }

    private fun processAudioSamples(samples: FloatArray) {
        if (samples.isEmpty()) return

        // Throttle to max 50fps
        val now = System.currentTimeMillis()
        if (now - lastPacketTime < UdpSoundSyncPacket.PACKET_INTERVAL_MS) {
            // Still accumulate samples for FFT even if we don't send
            accumulateSamples(samples)
            return
        }
        lastPacketTime = now

        // Run DSP pipeline
        val filtered = lowCutFilter.process(samples)
        val gained = gainControl.process(filtered)

        // Accumulate for FFT window
        accumulateSamples(gained)

        // Compute sample values
        val sampleRaw = computePeak(gained)
        val sampleSmth = computeAverage(gained)
        val samplePeak = beatDetector.detect(sampleRaw)

        // Run FFT on the accumulated window
        val fftWindow = getFftWindow()
        val fftResult = fftAnalyzer.analyze(fftWindow)

        // Build and send packet
        val packet = UdpSoundSyncPacket(
            sampleRaw = sampleRaw.coerceIn(0f, SAMPLE_MAX_VALUE),
            sampleSmth = sampleSmth.coerceIn(0f, SAMPLE_MAX_VALUE),
            samplePeak = samplePeak,
            fftResult = fftResult.fftChannels,
            fftMagnitude = fftResult.magnitude,
            fftMajorPeak = fftResult.majorPeak,
        )

        // Send on IO dispatcher (non-blocking for audio thread)
        managerScope.launch {
            withContext(ioDispatcher) {
                udpSender?.sendPacket(packet)
            }
        }

        // Update UI state (throttled — only update if values changed significantly)
        _state.update {
            it.copy(
                fftChannels = fftResult.fftChannels,
                sampleLevel = sampleSmth,
                isPeak = samplePeak,
            )
        }
    }

    private fun accumulateSamples(samples: FloatArray) {
        for (sample in samples) {
            sampleBuffer[sampleBufferIndex] = sample
            sampleBufferIndex = (sampleBufferIndex + 1) % FFT_WINDOW_SIZE
            if (sampleBufferIndex == 0) sampleBufferFilled = true
        }
    }

    private fun getFftWindow(): FloatArray {
        if (!sampleBufferFilled && sampleBufferIndex == 0) {
            return FloatArray(FFT_WINDOW_SIZE)
        }
        // Return samples in chronological order (oldest first)
        val window = FloatArray(FFT_WINDOW_SIZE)
        val start = if (sampleBufferFilled) sampleBufferIndex else 0
        for (i in 0 until FFT_WINDOW_SIZE) {
            window[i] = sampleBuffer[(start + i) % FFT_WINDOW_SIZE]
        }
        return window
    }

    private fun resetSampleBuffer() {
        sampleBuffer.fill(0f)
        sampleBufferIndex = 0
        sampleBufferFilled = false
    }

    private fun computePeak(samples: FloatArray): Float {
        var peak = 0f
        for (sample in samples) {
            val abs = abs(sample)
            if (abs > peak) peak = abs
        }
        return peak * SAMPLE_MAX_VALUE
    }

    private fun computeAverage(samples: FloatArray): Float {
        if (samples.isEmpty()) return 0f
        var sum = 0f
        for (sample in samples) {
            sum += abs(sample)
        }
        return sum / samples.size * SAMPLE_MAX_VALUE
    }

    private fun acquireMulticastLock() {
        runCatching {
            val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            wifiManager?.createMulticastLock(TAG)?.apply {
                setReferenceCounted(true)
                acquire()
            }?.let { multicastLock = it }
        }.onFailure { logger.w(it) { "Failed to acquire multicast lock" } }
    }

    private fun releaseMulticastLock() {
        runCatching { multicastLock?.release() }
        multicastLock = null
    }

    private fun startObservingDevices() {
        deviceListJob?.cancel()
        deviceListJob = managerScope.launch {
            deviceRepository.allDevices.collect { devices ->
                val addresses = devices
                    .filter { !it.isHidden }
                    .map { it.address }
                udpSender?.updateUnicastAddresses(addresses)
            }
        }
    }

    private fun cleanup() {
        deviceListJob?.cancel()
        deviceListJob = null
        udpSender?.close()
        udpSender = null
        releaseMulticastLock()
        lastPacketTime = 0L
    }

    companion object {
        private const val FFT_WINDOW_SIZE = FftAnalyzer.DEFAULT_FFT_SIZE

        /** Maximum sample value for UDP sound sync packets (0-255 range). */
        private const val SAMPLE_MAX_VALUE = 255f
    }
}
