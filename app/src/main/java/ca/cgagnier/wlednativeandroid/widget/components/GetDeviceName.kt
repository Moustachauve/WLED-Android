package ca.cgagnier.wlednativeandroid.widget.components

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.resolveDeviceName

/**
 * Returns the display name for a device following the hierarchy:
 * 1. Custom name (if not blank)
 * 2. Original name (if not blank)
 * 3. Fallback name
 *
 * This is a non-Composable version of the [ca.cgagnier.wlednativeandroid.ui.components.deviceName]
 * function that can be used outside of Compose context (e.g., in background services).
 */
fun getDeviceName(device: Device?, fallbackName: String): String = resolveDeviceName(device, fallbackName)
