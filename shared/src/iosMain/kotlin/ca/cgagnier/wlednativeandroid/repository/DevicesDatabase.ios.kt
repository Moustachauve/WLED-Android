package ca.cgagnier.wlednativeandroid.repository

import androidx.room.Room
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val documentDirectory = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}

fun DevicesDatabase.Companion.createDatabase(): DevicesDatabase {
    val dbFilePath = documentDirectory() + "/devices_database.db"
    return Room.databaseBuilder<DevicesDatabase>(
        name = dbFilePath,
    )
        .configureDevicesDatabase()
        .build()
}
