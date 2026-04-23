package ca.cgagnier.wlednativeandroid.util

/**
 * Maximum brightness value used by WLED (0-255 range)
 */
const val MAX_BRIGHTNESS = 255

/**
 * Maximum brightness percentage (0-100 range)
 */
const val MAX_BRIGHTNESS_PERCENT = 100f

/**
 * Converts WLED brightness (0-255) to percentage (0.0-100.0).
 *
 * @param brightness The brightness value from WLED (0-255)
 * @return The brightness as a percentage (0.0-100.0)
 */
fun brightnessToPercent(brightness: Int): Float =
    (brightness.coerceIn(0, MAX_BRIGHTNESS) / MAX_BRIGHTNESS.toFloat()) * MAX_BRIGHTNESS_PERCENT

/**
 * Converts percentage (0.0-100.0) to WLED brightness (0-255).
 *
 * @param percent The brightness percentage (0.0-100.0)
 * @return The brightness value for WLED (0-255)
 */
fun percentToBrightness(percent: Float): Int =
    ((percent.coerceIn(0f, MAX_BRIGHTNESS_PERCENT) / MAX_BRIGHTNESS_PERCENT) * MAX_BRIGHTNESS).toInt()
