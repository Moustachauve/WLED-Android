package ca.cgagnier.wlednativeandroid

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "WebViewHolder"

// Solution from https://medium.com/@nicholas.rose/keeping-webview-state-across-configuration-changes-8e071ee9de86
// Allows to keep the webview alive through device rotation and activity recreation
class WebViewHolder(context: Context) {
    var firstLoad = true

    private val webView: WebView = WebView(context)

    // Expose the WebView via StateFlow
    private val _webViewFlow = MutableStateFlow(webView)
    val webViewFlow: StateFlow<WebView> = _webViewFlow.asStateFlow()

    /**
     * Helper to detach the webview from its parent.
     * Can be called from the ViewModel or UI if manual cleanup is needed.
     */
    fun detachFromParent() {
        (webView.parent as? ViewGroup)?.removeView(webView)
    }
}