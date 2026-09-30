package ca.cgagnier.wlednativeandroid.shared

import okio.FileSystem
import platform.Foundation.NSDate
import platform.Foundation.NSRecursiveLock
import platform.Foundation.timeIntervalSince1970

actual fun getPlatformName(): String = "iOS"

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

internal actual class SynchronizedObject {
    val nsLock = NSRecursiveLock()
}

internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
    lock.nsLock.lock()
    try {
        return block()
    } finally {
        lock.nsLock.unlock()
    }
}

actual val fileSystem: FileSystem = FileSystem.SYSTEM
