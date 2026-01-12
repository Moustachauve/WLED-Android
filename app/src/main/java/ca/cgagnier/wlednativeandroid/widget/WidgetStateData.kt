package ca.cgagnier.wlednativeandroid.widget

import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Serializable
data class WidgetStateData(
    val macAddress: String,
    val address: String,
    val name: String,
    val isOn: Boolean,
    val isOnline: Boolean = true,
    val color: Int = -1, // Store as ARGB Int. -1 or default could indicate "unknown"
    val batteryLevel: Int? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
) {
    val lastUpdatedFormatted: String
        get() {
            return DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.SHORT,
                Locale.getDefault(),
            ).format(Date(lastUpdated))
        }
}
