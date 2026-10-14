package ca.cgagnier.wlednativeandroid

import android.app.Application
import ca.cgagnier.wlednativeandroid.di.appModule
import ca.cgagnier.wlednativeandroid.widget.WidgetPreviewPublisher
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DevicesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DevicesApplication)
            modules(appModule)
        }
        WidgetPreviewPublisher.publishIfNeeded(this)
    }
}
