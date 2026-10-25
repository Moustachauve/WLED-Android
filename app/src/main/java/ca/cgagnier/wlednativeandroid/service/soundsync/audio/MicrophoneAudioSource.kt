package ca.cgagnier.wlednativeandroid.service.soundsync.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MicrophoneAudioSource"
private val logger = Logger.withTag(TAG)

/**
 * Captures audio from the device microphone using [AudioRecord].
 *
 * Emits normalized float samples in [-1.0, 1.0] at 44100 Hz, mono.
 * Requires the RECORD_AUDIO permission.
 */
class MicrophoneAudioSource(
    private val context: Context,
    override val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val bufferSizeMultiplier: Int = 4,
) : AudioSource {

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    @Synchronized
    override fun start(onAudioData: (FloatArray) -> Unit): Boolean {
        if (_isRunning.value) return true
        if (!hasPermission()) {
            logger.w { "RECORD_AUDIO permission not granted" }
            return false
        }

        val (record, bufferSize) = createAudioRecord() ?: return false

        audioRecord = record
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            logger.e { "AudioRecord failed to start recording" }
            record.stop()
            record.release()
            audioRecord = null
            return false
        }

        _isRunning.value = true
        captureJob = scope.launch {
            captureLoop(record, bufferSize, onAudioData)
        }
        return true
    }

    private fun createAudioRecord(): Pair<AudioRecord, Int>? {
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBufferSize <= 0) {
            logger.e { "Failed to get min buffer size: $minBufferSize" }
            return null
        }
        val bufferSize = minBufferSize * bufferSizeMultiplier

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            logger.e { "AudioRecord failed to initialize" }
            record.release()
            return null
        }
        return record to bufferSize
    }

    @Synchronized
    override fun stop() {
        _isRunning.value = false
        captureJob?.cancel()
        captureJob = null
        audioRecord?.let { record ->
            runCatching { record.stop() }
            runCatching { record.release() }
        }
        audioRecord = null
    }

    fun destroy() {
        stop()
        scope.cancel()
    }

    private suspend fun captureLoop(record: AudioRecord, bufferSize: Int, onAudioData: (FloatArray) -> Unit) {
        val byteBuffer = ByteArray(bufferSize)
        withContext(Dispatchers.IO) {
            while (isActive && _isRunning.value) {
                val bytesRead = record.read(byteBuffer, 0, bufferSize)
                if (bytesRead > 0) {
                    val samples = pcm16BytesToFloat(byteBuffer, bytesRead)
                    onAudioData(samples)
                } else if (bytesRead < 0) {
                    logger.e { "AudioRecord read error: $bytesRead" }
                    break
                }
            }
        }
        _isRunning.value = false
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
    }
}
