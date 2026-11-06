package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import androidx.work.Data

/**
 * A widget tap that needs to reach the device, serialized as [WidgetActionWorker] input.
 *
 * @param appWidgetId The widget that was tapped.
 * @param targetIsOn The power state to send, or null to only refresh the widget.
 * @param requestedAtMillis When the tap happened.
 */
internal data class WidgetActionRequest(
    val appWidgetId: Int,
    val targetIsOn: Boolean?,
    val requestedAtMillis: Long = System.currentTimeMillis(),
) {
    val uniqueWorkName: String
        get() = if (targetIsOn != null) "widget-power-$appWidgetId" else "widget-refresh-$appWidgetId"

    /**
     * The power state to send to the device, or null if the widget should only be refreshed.
     *
     * A toggle that could not run soon after the tap (e.g. while waiting for a network) is dropped
     * so the light doesn't unexpectedly switch minutes later.
     */
    fun powerStateToSend(nowMillis: Long): Boolean? =
        targetIsOn?.takeIf { nowMillis - requestedAtMillis <= MAX_TOGGLE_DELAY_MILLIS }

    fun toInputData(): Data = Data.Builder()
        .putInt(KEY_APP_WIDGET_ID, appWidgetId)
        .putLong(KEY_REQUESTED_AT, requestedAtMillis)
        .apply { targetIsOn?.let { putBoolean(KEY_TARGET_IS_ON, it) } }
        .build()

    companion object {
        internal const val MAX_TOGGLE_DELAY_MILLIS = 30_000L

        private const val KEY_APP_WIDGET_ID = "appWidgetId"
        private const val KEY_TARGET_IS_ON = "targetIsOn"
        private const val KEY_REQUESTED_AT = "requestedAt"

        fun fromInputData(data: Data): WidgetActionRequest? {
            val appWidgetId = data.getInt(KEY_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
            return WidgetActionRequest(
                appWidgetId = appWidgetId,
                targetIsOn = data.keyValueMap[KEY_TARGET_IS_ON] as? Boolean,
                requestedAtMillis = data.getLong(KEY_REQUESTED_AT, 0L),
            )
        }
    }
}
