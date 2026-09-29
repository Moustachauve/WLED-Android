package ca.cgagnier.wlednativeandroid.shared

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun getPlatformName(): String = "iOS"

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
