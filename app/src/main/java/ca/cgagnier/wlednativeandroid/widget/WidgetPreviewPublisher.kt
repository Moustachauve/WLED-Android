package ca.cgagnier.wlednativeandroid.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.collection.intSetOf
import androidx.core.content.edit
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetManager.Companion.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
import ca.cgagnier.wlednativeandroid.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Publishes widget previews for the launcher widget picker (Android 15+).
 * Handles rate limiting by checking if preview is already published and tracking app version.
 */
object WidgetPreviewPublisher {

    private const val PREFS_NAME = "widget_preview_prefs"
    private const val KEY_PREVIEW_VERSION = "preview_published_version"

    /**
     * Publishes widget previews if needed. Should be called during app initialization.
     * This is a no-op on Android < 15.
     */
    fun publishIfNeeded(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            publishPreviewsInternal(context)
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun publishPreviewsInternal(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            // Check if preview is already published for home screen category
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, WledWidgetReceiver::class.java)
            val providerInfo = appWidgetManager.getInstalledProvidersForPackage(context.packageName, null)
                .firstOrNull { it.provider == componentName }

            val hasHomeScreenPreview = providerInfo?.generatedPreviewCategories
                ?.and(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0

            // Also check if app was updated (preview might need refresh)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentVersionCode = BuildConfig.VERSION_CODE
            val publishedVersionCode = prefs.getInt(KEY_PREVIEW_VERSION, -1)
            val isNewVersion = publishedVersionCode != currentVersionCode

            // Skip if preview exists and we're on the same version
            if (hasHomeScreenPreview && !isNewVersion) {
                return@launch
            }

            val result = GlanceAppWidgetManager(context)
                .setWidgetPreviews(
                    WledWidgetReceiver::class,
                    intSetOf(AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN),
                )

            // Only save version if successful (not rate-limited)
            if (result == SET_WIDGET_PREVIEWS_RESULT_SUCCESS) {
                prefs.edit { putInt(KEY_PREVIEW_VERSION, currentVersionCode) }
            }
        }
    }
}
