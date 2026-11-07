package ca.cgagnier.wlednativeandroid.service.soundsync

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

private const val TAG = "SoundSyncService"
private val logger = Logger.withTag(TAG)

/**
 * Foreground service that keeps sound sync running while the app is in the
 * background. Shows a persistent notification with a stop action.
 */
class SoundSyncService : Service() {

    private val soundSyncManager: SoundSyncManager by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var stateMonitorJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundWithNotification()
                if (!hasRecordAudioPermission()) {
                    logger.w { "RECORD_AUDIO permission not granted, cannot start sound sync" }
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
                val gain = intent.getFloatExtra(EXTRA_GAIN, 1.0f)
                val agcEnabled = intent.getBooleanExtra(EXTRA_AGC_ENABLED, false)
                val agcModeName = intent.getStringExtra(EXTRA_AGC_MODE)
                val agcMode = agcModeName?.let { name ->
                    AgcMode.entries.firstOrNull { it.name == name }
                } ?: AgcMode.NORMAL
                val squelch = intent.getIntExtra(EXTRA_SQUELCH, SoundSyncProcessor.DEFAULT_SQUELCH)
                val started = soundSyncManager.start(gain, agcEnabled, agcMode, squelch)
                if (!started) {
                    ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    observeRunningState()
                }
            }

            ACTION_STOP -> {
                stateMonitorJob?.cancel()
                soundSyncManager.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            else -> {
                startForegroundWithNotification()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeRunningState() {
        stateMonitorJob?.cancel()
        stateMonitorJob = serviceScope.launch {
            soundSyncManager.state.collect { state ->
                if (!state.isRunning) {
                    ServiceCompat.stopForeground(
                        this@SoundSyncService,
                        ServiceCompat.STOP_FOREGROUND_REMOVE,
                    )
                    stopSelf()
                }
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        serviceScope.cancel()
        soundSyncManager.stop()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val notification = buildNotification()
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType,
            )
        }.onFailure {
            logger.w(it) { "Failed to promote SoundSyncService to foreground" }
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sound_sync_notification_title))
            .setContentText(getString(R.string.sound_sync_notification_text))
            .setSmallIcon(R.drawable.baseline_graphic_eq_24)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.sound_sync_notification_stop),
                stopPendingIntent,
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(getString(R.string.sound_sync_notification_channel))
            .setDescription(getString(R.string.sound_sync_notification_channel_description))
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "sound_sync_channel"
        private const val NOTIFICATION_ID = 4001

        const val ACTION_START = "ca.cgagnier.wlednativeandroid.action.START_SOUND_SYNC"
        const val ACTION_STOP = "ca.cgagnier.wlednativeandroid.action.STOP_SOUND_SYNC"
        const val EXTRA_GAIN = "extra_gain"
        const val EXTRA_AGC_ENABLED = "extra_agc_enabled"
        const val EXTRA_AGC_MODE = "extra_agc_mode"
        const val EXTRA_SQUELCH = "extra_squelch"

        fun startIntent(context: Context, gain: Float, agcEnabled: Boolean, agcMode: AgcMode, squelch: Int): Intent =
            Intent(context, SoundSyncService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_GAIN, gain)
                putExtra(EXTRA_AGC_ENABLED, agcEnabled)
                putExtra(EXTRA_AGC_MODE, agcMode.name)
                putExtra(EXTRA_SQUELCH, squelch)
            }

        fun stopIntent(context: Context): Intent = Intent(context, SoundSyncService::class.java).apply {
            action = ACTION_STOP
        }
    }
}
