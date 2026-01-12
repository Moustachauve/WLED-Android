package ca.cgagnier.wlednativeandroid.widget

import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import ca.cgagnier.wlednativeandroid.R

/**
 * Displays a live ticking elapsed time counter using a native Chronometer view.
 *
 * This uses AndroidRemoteViews to embed a legacy XML layout containing a Chronometer,
 * which ticks independently without requiring widget recomposition.
 *
 * The Chronometer base is calculated by converting the wall-clock lastUpdated timestamp
 * to the corresponding elapsedRealtime value that the Chronometer expects.
 *
 * @param lastUpdated The wall-clock timestamp (from System.currentTimeMillis()) to count up from.
 */
@Composable
fun ElapsedTimeChronometer(lastUpdated: Long) {
    val context = LocalContext.current
    val outlineColor = GlanceTheme.colors.outline.getColor(context).toArgb()

    // Calculate the Chronometer base:
    // Chronometer uses SystemClock.elapsedRealtime() as reference.
    // We need to convert our wall-clock lastUpdated to elapsedRealtime base.
    // elapsedRealtime_at_lastUpdated = currentElapsedRealtime - (currentTime - lastUpdated)
    val currentTime = System.currentTimeMillis()
    val elapsedSinceUpdate = currentTime - lastUpdated
    val chronometerBase = SystemClock.elapsedRealtime() - elapsedSinceUpdate

    AndroidRemoteViews(
        remoteViews = RemoteViews(context.packageName, R.layout.widget_last_updated).apply {
            setChronometer(R.id.chronometer, chronometerBase, null, true)
            setTextColor(R.id.chronometer, outlineColor)
            setInt(R.id.history_icon, "setColorFilter", outlineColor)
        },
    )
}
