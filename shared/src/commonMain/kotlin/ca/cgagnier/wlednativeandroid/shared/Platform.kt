package ca.cgagnier.wlednativeandroid.shared

import kotlinx.coroutines.CoroutineDispatcher
import okio.FileSystem

expect fun getPlatformName(): String

expect fun currentTimeMillis(): Long

expect class SynchronizedObject()

expect inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R

expect val ioDispatcher: CoroutineDispatcher

expect val fileSystem: FileSystem
