package ca.cgagnier.wlednativeandroid.widget

import kotlinx.serialization.Serializable

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
    // TODO: Add support for quick actions
    val quickActionsEnabled: Boolean = false,
)
