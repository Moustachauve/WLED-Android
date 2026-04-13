package ca.cgagnier.wlednativeandroid.service.audio

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioCaptureService : Service() {

    private val binder = AudioSyncBinder()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private val processor = AudioProcessor()
    private val sender = UdpSyncSender()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentAudioData = MutableStateFlow<AudioData?>(null)
    val currentAudioData: StateFlow<AudioData?> = _currentAudioData.asStateFlow()

    private var targetAddress = UdpSyncSender.DEFAULT_MULTICAST_ADDRESS
    private var targetPort = UdpSyncSender.DEFAULT_PORT
    private var gain = 1.0f

    inner class AudioSyncBinder : Binder() {
        fun getService(): AudioCaptureService = this@AudioCaptureService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                targetAddress = intent.getStringExtra(EXTRA_TARGET_ADDRESS)
                    ?: UdpSyncSender.DEFAULT_MULTICAST_ADDRESS
                targetPort = intent.getIntExtra(EXTRA_TARGET_PORT, UdpSyncSender.DEFAULT_PORT)
                gain = intent.getFloatExtra(EXTRA_GAIN, 1.0f)
                startCapture()
            }
            ACTION_STOP -> stopCapture()
            ACTION_UPDATE_GAIN -> {
                gain = intent.getFloatExtra(EXTRA_GAIN, 1.0f)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    fun updateGain(newGain: Float) {
        gain = newGain
    }

    @Suppress("MissingPermission") // Permission is checked before calling
    private fun startCapture() {
        if (_isRunning.value) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            stopSelf()
            return
        }

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val bufferSize = AudioRecord.getMinBufferSize(
            AudioProcessor.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(AudioProcessor.FFT_SIZE * 2) // Ensure buffer holds at least one FFT frame

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            AudioProcessor.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize,
        )

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord failed to initialize")
            stopSelf()
            return
        }

        sender.open()
        audioRecord?.startRecording()
        _isRunning.value = true

        captureJob = scope.launch {
            val samples = ShortArray(AudioProcessor.FFT_SIZE)
            val intervalMs = SEND_INTERVAL_MS

            while (isActive && _isRunning.value) {
                val startTime = System.currentTimeMillis()

                val read = audioRecord?.read(samples, 0, AudioProcessor.FFT_SIZE) ?: -1
                if (read > 0) {
                    val audioData = processor.process(samples, gain)
                    _currentAudioData.value = audioData
                    sender.send(audioData, targetAddress, targetPort)
                }

                // Maintain ~50 packets/sec
                val elapsed = System.currentTimeMillis() - startTime
                val sleepTime = intervalMs - elapsed
                if (sleepTime > 0) {
                    delay(sleepTime)
                }
            }
        }

        Log.i(TAG, "Audio capture started -> $targetAddress:$targetPort")
    }

    private fun stopCapture() {
        _isRunning.value = false
        captureJob?.cancel()
        captureJob = null

        audioRecord?.apply {
            try {
                stop()
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Error stopping AudioRecord", e)
            }
            release()
        }
        audioRecord = null

        sender.close()
        _currentAudioData.value = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()

        Log.i(TAG, "Audio capture stopped")
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Audio Sync",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "WLED Audio Sync is streaming microphone data"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, AudioCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WLED Audio Sync")
            .setContentText("Streaming audio to $targetAddress")
            .setSmallIcon(R.drawable.ic_baseline_router_24)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    companion object {
        private const val TAG = "AudioCaptureService"
        private const val CHANNEL_ID = "audio_sync_channel"
        private const val NOTIFICATION_ID = 9001
        private const val SEND_INTERVAL_MS = 20L // ~50 packets/sec

        const val ACTION_START = "ca.cgagnier.wlednativeandroid.audio.START"
        const val ACTION_STOP = "ca.cgagnier.wlednativeandroid.audio.STOP"
        const val ACTION_UPDATE_GAIN = "ca.cgagnier.wlednativeandroid.audio.UPDATE_GAIN"
        const val EXTRA_TARGET_ADDRESS = "target_address"
        const val EXTRA_TARGET_PORT = "target_port"
        const val EXTRA_GAIN = "gain"
    }
}
