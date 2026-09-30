package ca.cgagnier.wlednativeandroid.shared

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okio.FileSystem

actual fun getPlatformName(): String = "Android"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual typealias SynchronizedObject = Any

actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R = kotlin.synchronized(lock, block)

actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

actual val fileSystem: FileSystem = FileSystem.SYSTEM
