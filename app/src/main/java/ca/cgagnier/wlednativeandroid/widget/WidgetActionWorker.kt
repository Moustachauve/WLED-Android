package ca.cgagnier.wlednativeandroid.widget

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ca.cgagnier.wlednativeandroid.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Sends widget taps to the device from expedited work instead of the Glance action callback.
 *
 * Glance runs action callbacks in a background broadcast receiver, which Android cuts off from the
 * network while Battery Saver is on: the request stalls until the app is opened. Expedited work
 * with a network constraint is exempt from Battery Saver, Doze and App Standby network
 * restrictions, so the tap reaches the device right away.
 */
class WidgetActionWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params),
    KoinComponent {
    private val widgetManager: WledWidgetManager by inject()

    override suspend fun doWork(): Result {
        val request = WidgetActionRequest.fromInputData(inputData) ?: return Result.failure()
        val glanceId = try {
            GlanceAppWidgetManager(applicationContext).getGlanceIdBy(request.appWidgetId)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Widget ${request.appWidgetId} was removed before its action ran", e)
            return Result.success()
        }

        val powerState = request.powerStateToSend(System.currentTimeMillis())
        if (powerState != null) {
            widgetManager.toggleState(applicationContext, glanceId, powerState)
        } else {
            widgetManager.refreshWidget(applicationContext, glanceId)
        }
        return Result.success()
    }

    /** Only called below Android 12, where expedited work runs as a foreground service. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val channel = NotificationChannelCompat.Builder(
            NOTIFICATION_CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName(applicationContext.getString(R.string.widget_action_notification_channel))
            .build()
        NotificationManagerCompat.from(applicationContext).createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.widget_action_notification_title))
            .setSmallIcon(R.drawable.outline_power_settings_new_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val TAG = "WidgetActionWorker"
        private const val NOTIFICATION_CHANNEL_ID = "widget_action_channel"
        private const val NOTIFICATION_ID = 4002

        internal fun enqueue(context: Context, request: WidgetActionRequest) {
            // The network constraint is what exempts expedited work from Battery Saver.
            val workRequest = OneTimeWorkRequestBuilder<WidgetActionWorker>()
                .setInputData(request.toInputData())
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(request.uniqueWorkName, ExistingWorkPolicy.REPLACE, workRequest)
        }
    }
}
