package ca.cgagnier.wlednativeandroid.shared

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import okio.FileSystem
import platform.Foundation.NSDate
import platform.Foundation.NSRecursiveLock
import platform.Foundation.timeIntervalSince1970

actual fun getPlatformName(): String = "iOS"

actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual class SynchronizedObject {
    val nsLock = NSRecursiveLock()
}

actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R {
    lock.nsLock.lock()
    try {
        return block()
    } finally {
        lock.nsLock.unlock()
    }
}

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual val fileSystem: FileSystem = FileSystem.SYSTEM
