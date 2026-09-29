package ca.cgagnier.wlednativeandroid.repository

import android.content.Context
import androidx.room.Room

fun DevicesDatabase.Companion.getDatabase(context: Context): DevicesDatabase =
    DevicesDatabaseSingleton.getInstance(context)

private object DevicesDatabaseSingleton {
    @Volatile
    private var instance: DevicesDatabase? = null

    fun getInstance(context: Context): DevicesDatabase = instance ?: synchronized(this) {
        instance ?: run {
            val appContext = context.applicationContext
            Room.databaseBuilder(
                appContext,
                DevicesDatabase::class.java,
                "devices_database",
            )
                .configureDevicesDatabase()
                .build()
                .also { instance = it }
        }
    }
}
