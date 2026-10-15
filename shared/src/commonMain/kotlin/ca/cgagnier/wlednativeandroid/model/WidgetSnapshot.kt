package ca.cgagnier.wlednativeandroid.model

import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.shared.currentTimeMillis
import kotlinx.serialization.Serializable

/**
 * Platform-agnostic, lightweight projection of a device's essential state
 * consumed by home screen widgets and glanceables.
 */
@Serializable
data class WidgetSnapshot(
    val macAddress: String,
    val address: String,
    val name: String,
    val isOn: Boolean,
    val isOnline: Boolean,
    val color: Int = -1, // ARGB Int
    val brightness: Int? = null,
    val lastUpdated: Long = 0L,
)

/**
 * Projects an immutable [DeviceWithState] into a [WidgetSnapshot].
 */
fun DeviceWithState.toWidgetSnapshot(
    defaultName: String = "",
    fallbackColor: Int = -1,
    now: Long = currentTimeMillis(),
): WidgetSnapshot {
    val state = stateInfo?.state
    val color = getColorFromState(state) ?: fallbackColor
    val resolvedName = device.customName.trim().ifBlank {
        device.originalName.trim().ifBlank { defaultName }
    }
    return WidgetSnapshot(
        macAddress = device.macAddress,
        address = device.address,
        name = resolvedName,
        isOn = state?.isOn ?: false,
        isOnline = isOnline,
        color = color,
        brightness = state?.brightness,
        lastUpdated = now,
    )
}

/**
 * Extracts ARGB 32-bit color integer from the first segment's primary color,
 * or returns null if no valid segment color is available.
 */
@Suppress("MagicNumber")
fun getColorFromState(state: State?): Int? {
    val colors = state?.segment?.firstOrNull()?.colors?.firstOrNull() ?: return null
    if (colors.size !in 3..4) return null
    val r = colors[0]
    val g = colors[1]
    val b = colors[2]
    return (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
}
