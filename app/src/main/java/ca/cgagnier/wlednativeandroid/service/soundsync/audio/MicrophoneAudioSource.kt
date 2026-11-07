package ca.cgagnier.wlednativeandroid.service.soundsync.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

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
    coroutineScope: CoroutineScope,
    ioDispatcher: CoroutineDispatcher,
    override val sampleRate: Int = DEFAULT_SAMPLE_RATE,
    private val bufferSizeMultiplier: Int = DEFAULT_BUFFER_SIZE_MULTIPLIER,
) : AudioSource {

    private val scope = CoroutineScope(
        coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]) + ioDispatcher,
    )

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var captureJob: Job? = null

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @Synchronized
    override fun start(onAudioData: (FloatArray) -> Unit): Boolean {
        if (_isRunning.value) return true
        if (!hasPermission()) {
            logger.w { "RECORD_AUDIO permission not granted" }
            return false
        }

        val (record, readChunkSamples) = createAndStartAudioRecord() ?: return false

        _isRunning.value = true
        captureJob = scope.launch {
            captureLoop(record, readChunkSamples, onAudioData)
        }
        return true
    }

    private fun createAndStartAudioRecord(): Pair<AudioRecord, Int>? {
        if (
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBufferSize <= 0) {
            logger.e { "Failed to get min buffer size: $minBufferSize" }
            return null
        }
        val internalBufferSize = minBufferSize * bufferSizeMultiplier
        // Read ~10ms sub-frames (e.g. 441 samples at 44.1 kHz) so the processor's 20ms
        // sample-clock deadline emits a steady 50 Hz packet stream.
        val maxSamplesInBuffer = (internalBufferSize / BYTES_PER_SAMPLE_FLOAT).coerceAtLeast(1)
        val readChunkSamples = (sampleRate / TARGET_CHUNKS_PER_SECOND).coerceAtMost(maxSamplesInBuffer)

        for (audioSource in candidateAudioSources()) {
            val record = tryStartAudioRecord(audioSource, internalBufferSize)
            if (record != null) {
                return record to readChunkSamples
            }
        }
        return null
    }

    /**
     * Prefers [MediaRecorder.AudioSource.UNPROCESSED] when supported by hardware so platform AGC
     * and noise suppression do not interfere with WLED DSP (matching iOS `.measurement` mode),
     * falling back to [MediaRecorder.AudioSource.VOICE_RECOGNITION] and [MediaRecorder.AudioSource.MIC].
     */
    private fun candidateAudioSources(): List<Int> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val supportsUnprocessed =
            audioManager?.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED).toBoolean()
        return if (supportsUnprocessed) {
            listOf(
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            )
        } else {
            listOf(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC,
            )
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun tryStartAudioRecord(audioSource: Int, internalBufferSize: Int): AudioRecord? {
        @Suppress("TooGenericExceptionCaught")
        val record = try {
            AudioRecord(
                audioSource,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                internalBufferSize,
            )
        } catch (e: Exception) {
            // AudioRecord constructor can throw IllegalArgumentException,
            // UnsupportedOperationException, or SecurityException
            logger.w(e) { "Failed to create AudioRecord for source $audioSource" }
            return null
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            logger.w { "AudioRecord failed to initialize for source $audioSource" }
            record.release()
            return null
        }
        record.startRecording()
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            logger.w { "AudioRecord failed to start recording for source $audioSource" }
            runCatching { record.stop() }
            runCatching { record.release() }
            return null
        }
        return record
    }

    @Synchronized
    override fun stop() {
        _isRunning.value = false
        captureJob?.cancel()
        captureJob = null
    }

    override fun destroy() {
        stop()
        scope.cancel()
    }

    private fun CoroutineScope.captureLoop(
        record: AudioRecord,
        readChunkSamples: Int,
        onAudioData: (FloatArray) -> Unit,
    ) {
        val floatBuffer = FloatArray(readChunkSamples)
        try {
            while (isActive && _isRunning.value) {
                val samplesRead = record.read(floatBuffer, 0, readChunkSamples, AudioRecord.READ_BLOCKING)
                if (samplesRead < 0) {
                    logger.e { "AudioRecord read error: $samplesRead" }
                    _isRunning.value = false
                    break
                } else if (samplesRead > 0 && isActive && _isRunning.value) {
                    onAudioData(floatBuffer.copyOf(samplesRead))
                }
            }
        } finally {
            runCatching { record.stop() }
            runCatching { record.release() }
        }
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44100
        private const val DEFAULT_BUFFER_SIZE_MULTIPLIER = 4
        private const val TARGET_CHUNKS_PER_SECOND = 100
        private const val BYTES_PER_SAMPLE_FLOAT = 4
    }
}
