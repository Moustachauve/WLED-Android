package ca.cgagnier.wlednativeandroid.shared

import okio.FileSystem

expect fun getPlatformName(): String

expect fun currentTimeMillis(): Long

internal expect class SynchronizedObject()

internal expect inline fun <R> synchronized(lock: SynchronizedObject, block: () -> R): R

expect val fileSystem: FileSystem
