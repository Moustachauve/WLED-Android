package ca.cgagnier.wlednativeandroid.service.soundsync

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.SoundSyncAudioSourceType
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import co.touchlab.kermit.Logger
import org.koin.android.ext.android.inject

private const val TAG = "SoundSyncService"
private val logger = Logger.withTag(TAG)

/**
 * Foreground service that keeps sound sync running while the app is in the
 * background. Shows a persistent notification with a stop action.
 */
class SoundSyncService : Service() {

    private val soundSyncManager: SoundSyncManager by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!hasRecordAudioPermission()) {
                    logger.w { "RECORD_AUDIO permission not granted, cannot start sound sync" }
                    stopSelf()
                    return START_NOT_STICKY
                }
                val sourceType = intent.getStringExtra(EXTRA_SOURCE_TYPE)
                    ?.let { SoundSyncAudioSourceType.valueOf(it) }
                    ?: SoundSyncAudioSourceType.MICROPHONE
                val gain = intent.getFloatExtra(EXTRA_GAIN, 1.0f)
                val agcEnabled = intent.getBooleanExtra(EXTRA_AGC_ENABLED, false)
                startForegroundWithNotification()
                soundSyncManager.start(sourceType, gain, agcEnabled)
            }

            ACTION_STOP -> {
                soundSyncManager.stop()
                stopSelf()
            }

            else -> {
                // Service was restarted by the system
                if (soundSyncManager.state.value.isRunning) {
                    startForegroundWithNotification()
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun hasRecordAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        soundSyncManager.stop()
        super.onDestroy()
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(NOTIFICATION_ID, notification)
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

        val stopIntent = Intent(this, SoundSyncService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.sound_sync_notification_title))
            .setContentText(getString(R.string.sound_sync_notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
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
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.sound_sync_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.sound_sync_notification_channel_description)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "sound_sync_channel"
        private const val NOTIFICATION_ID = 4001

        const val ACTION_START = "ca.cgagnier.wlednativeandroid.action.START_SOUND_SYNC"
        const val ACTION_STOP = "ca.cgagnier.wlednativeandroid.action.STOP_SOUND_SYNC"
        const val EXTRA_SOURCE_TYPE = "extra_source_type"
        const val EXTRA_GAIN = "extra_gain"
        const val EXTRA_AGC_ENABLED = "extra_agc_enabled"

        fun startIntent(
            context: Context,
            sourceType: SoundSyncAudioSourceType,
            gain: Float,
            agcEnabled: Boolean,
        ): Intent = Intent(context, SoundSyncService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_SOURCE_TYPE, sourceType.name)
            putExtra(EXTRA_GAIN, gain)
            putExtra(EXTRA_AGC_ENABLED, agcEnabled)
        }

        fun stopIntent(context: Context): Intent = Intent(context, SoundSyncService::class.java).apply {
            action = ACTION_STOP
        }
    }
}
