package ca.cgagnier.wlednativeandroid

import android.app.Application
import ca.cgagnier.wlednativeandroid.widget.WidgetPreviewPublisher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DevicesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        WidgetPreviewPublisher.publishIfNeeded(this)
    }
}
