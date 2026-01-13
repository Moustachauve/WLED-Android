package ca.cgagnier.wlednativeandroid.util

import android.content.ActivityNotFoundException
import android.util.Log
import androidx.compose.ui.platform.UriHandler

private const val TAG = "UriUtils"

/**
 * Open Uri in external browser with error handling.
 *
 * Errors can happen if, for example, a user doesn't have any browser installed.
 */
fun UriHandler.openUriSafely(uri: String) {
    try {
        this.openUri(uri)
    } catch (e: IllegalArgumentException) {
        Log.e(TAG, "Invalid URI: $uri", e)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No browser found to open: $uri", e)
    }
}
