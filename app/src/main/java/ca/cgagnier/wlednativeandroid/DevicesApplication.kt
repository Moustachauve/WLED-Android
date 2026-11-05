package ca.cgagnier.wlednativeandroid

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import ca.cgagnier.wlednativeandroid.di.appModule
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.WidgetStateSync
import ca.cgagnier.wlednativeandroid.widget.WidgetPreviewPublisher
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DevicesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@DevicesApplication)
            modules(appModule)
        }.koin

        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    val connectionManager: DeviceConnectionManager = koin.get()
                    val widgetStateSync: WidgetStateSync = koin.get()
                    widgetStateSync.start()
                    connectionManager.onForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    val connectionManager: DeviceConnectionManager = koin.get()
                    val widgetStateSync: WidgetStateSync = koin.get()
                    widgetStateSync.stop()
                    connectionManager.onBackground()
                }
            })
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.w("DevicesApplication", "ProcessLifecycleOwner not available: ${e.message}")
        }

        WidgetPreviewPublisher.publishIfNeeded(this)
    }
}
