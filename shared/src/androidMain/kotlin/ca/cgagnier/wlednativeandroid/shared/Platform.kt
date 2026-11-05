package ca.cgagnier.wlednativeandroid.shared

import okio.FileSystem

actual fun getPlatformName(): String = "Android"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual typealias SynchronizedObject = Any

internal actual inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R =
    kotlin.synchronized(lock, block)

actual val fileSystem: FileSystem = FileSystem.SYSTEM
