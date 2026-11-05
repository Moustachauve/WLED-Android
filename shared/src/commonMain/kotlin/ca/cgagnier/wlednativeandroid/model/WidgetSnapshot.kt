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
    val signalLevel: WifiSignalLevel = if (isOnline) WifiSignalLevel.GOOD else WifiSignalLevel.OFFLINE,
    val lastUpdated: Long = 0L,
)

/**
 * Projects an immutable [DeviceWithState] into a [WidgetSnapshot].
 */
fun DeviceWithState.toWidgetSnapshot(
    defaultName: String = "",
    fallbackColor: Int = -1,
    now: Long = currentTimeMillis(),
): WidgetSnapshot = createWidgetSnapshotFromState(
    device = device,
    targetAddress = device.address,
    defaultName = defaultName,
    state = stateInfo?.state,
    isOnline = isOnline,
    fallbackColor = fallbackColor,
    fallbackSignalLevel = WifiSignalLevel.fromRssi(
        rssi = stateInfo?.info?.wifi?.rssi,
        isOnline = isOnline,
    ),
    now = now,
)

/**
 * Resolves the display name for a [Device] following the hierarchy:
 * 1. Custom name (if not blank)
 * 2. Original name (if not blank)
 * 3. [defaultName]
 */
fun resolveDeviceName(device: Device?, defaultName: String): String =
    device?.customName?.trim()?.takeIf { it.isNotEmpty() }
        ?: device?.originalName?.trim()?.takeIf { it.isNotEmpty() }
        ?: defaultName

/**
 * Projects a [Device] and optional [State] into a [WidgetSnapshot].
 *
 * Shared by live WebSocket state projection ([toWidgetSnapshot]) and direct HTTP widget/shortcut
 * refreshes on Android and iOS.
 */
@Suppress("LongParameterList")
fun createWidgetSnapshotFromState(
    device: Device?,
    targetAddress: String,
    defaultName: String,
    state: State?,
    isOnline: Boolean,
    macAddress: String = device?.macAddress ?: "",
    fallbackIsOn: Boolean = false,
    fallbackColor: Int = -1,
    fallbackSignalLevel: WifiSignalLevel = WifiSignalLevel.GOOD,
    now: Long = currentTimeMillis(),
): WidgetSnapshot {
    val color = getColorFromState(state) ?: fallbackColor
    val resolvedName = resolveDeviceName(device, defaultName)
    val resolvedSignalLevel = if (isOnline) {
        fallbackSignalLevel.takeUnless { it == WifiSignalLevel.OFFLINE } ?: WifiSignalLevel.GOOD
    } else {
        WifiSignalLevel.OFFLINE
    }
    return WidgetSnapshot(
        macAddress = macAddress,
        address = targetAddress,
        name = resolvedName,
        isOn = state?.isOn ?: fallbackIsOn,
        isOnline = isOnline,
        color = color,
        brightness = state?.brightness,
        signalLevel = resolvedSignalLevel,
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
