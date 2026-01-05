/*
 * Modified version of https://github.com/google/accompanist/blob/main/web/src/main/java/com/google/accompanist/web/WebView.kt
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.cgagnier.wlednativeandroid.ui.components

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import ca.cgagnier.wlednativeandroid.FileUploadContract
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.ui.MainActivity
import ca.cgagnier.wlednativeandroid.ui.components.LoadingState.Finished
import ca.cgagnier.wlednativeandroid.ui.components.LoadingState.Loading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


private const val TAG = "ui.components.DeviceWebView"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeviceWebView(
    device: Device,
    webViewViewModel: WebViewViewModel = viewModel(
        factory = WebViewViewModel.Factory(
            context = LocalContext.current
        )
    ),
    state: WebViewState,
    navigator: WebViewNavigator = rememberWebViewNavigator(),
    client: CustomWebViewClient = remember { CustomWebViewClient() },
    context: Context = LocalContext.current,
    chromeClient: CustomWebChromeClient = remember { CustomWebChromeClient(context) },
) {
    Log.i(TAG, "composing webview")
    val webView = webViewViewModel.webView().collectAsState().value
    navigator.backQueue = webViewViewModel.backQueue
    var showDownloadCompleteToast by remember { mutableStateOf(false) }

    // Add this DisposableEffect to prevent memory leaks and crashes on configuration changes.
    DisposableEffect(webView) {
        onDispose {
            // The WebView is reused, so we must remove it from its old parent
            // before it can be attached to a new one.
            Log.d(TAG, "Removing webview onDispose")
            (webView.parent as? ViewGroup)?.removeView(webView)
        }
    }
    // Auto-dismiss the snackbar after 8 seconds
    LaunchedEffect(showDownloadCompleteToast) {
        if (showDownloadCompleteToast) {
            delay(8000)
            showDownloadCompleteToast = false
        }
    }

    BackHandler(navigator.canGoBack) {
        Log.i(TAG, "back handler triggered")
        navigator.goBackLogic()
    }
    LaunchedEffect(webView, navigator) {
        with(navigator) {
            webView.handleNavigationEvents()
        }
    }
    LaunchedEffect(webView, state) {
        snapshotFlow { state.content }.collect { content ->
            when (content) {
                is WebContent.Url -> {
                    webView.loadUrl(content.url, content.additionalHttpHeaders)
                }

                is WebContent.Data -> {
                    webView.loadDataWithBaseURL(
                        content.baseUrl,
                        content.data,
                        content.mimeType,
                        content.encoding,
                        content.historyUrl
                    )
                }

                is WebContent.Post -> {
                    webView.postUrl(
                        content.url,
                        content.postData
                    )
                }

                is WebContent.NavigatorOnly -> {
                    // NO-OP
                }
            }
        }
    }

    client.state = state
    client.navigator = navigator
    chromeClient.state = state

    fun resetWebview() {
        webView.loadUrl("about:blank")
        navigator.reset()
        Log.i(TAG, "Navigating to ${device.getDeviceUrl()}")
        state.loadingState = Loading(0.0f)
        state.isError = false
        navigator.loadUrl(device.getDeviceUrl())
    }

    if (webViewViewModel.displayedDevice == null || webViewViewModel.displayedDevice?.address != device.address) {
        webViewViewModel.displayedDevice = device
        Log.i(TAG, "Device changed, resetting")
        resetWebview()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.isError) {
            DeviceErrorScreen(
                onRetry = {
                    resetWebview()
                }
            )
        } else {
            AndroidView(
                factory = {
                    webView.apply {
                        clipToOutline = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        webView.setBackgroundColor(Color.TRANSPARENT)
                        webChromeClient = chromeClient
                        webViewClient = client

                        if (webViewViewModel.firstLoad) {
                            webViewViewModel.firstLoad = false

                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                        }
                    }
                    webView
                },
                update = { view ->
                    Log.d(TAG, "update!")

                    view.setDownloadListener { url, _, contentDisposition, mimetype, _ ->
                        downloadListener(
                            device,
                            url,
                            contentDisposition,
                            mimetype,
                            context,
                            onDownloadSuccess = {
                                showDownloadCompleteToast = true
                            }
                        )
                    }
                    //it.loadUrl(url)
                },
            )
        }
        if (showDownloadCompleteToast) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = {
                        openDownloadedFile(context)
                        showDownloadCompleteToast = false
                    }) {
                        Text(stringResource(R.string.open))
                    }
                },
                dismissAction = {
                    TextButton(onClick = { showDownloadCompleteToast = false }) {
                        Text(stringResource(R.string.dismiss))
                    }
                }
            ) {
                Text(stringResource(R.string.download_completed))
            }
        }
    }
}

@Composable
fun DeviceErrorScreen(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Image(painter = painterResource(id = R.drawable.akemi_018_teeth), contentDescription = null)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.device_error_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.device_error_message),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetry) {
            Text(text = stringResource(R.string.retry_connection))
        }
    }
}

class CustomWebViewClient : WebViewClient() {
    lateinit var state: WebViewState
        internal set
    lateinit var navigator: WebViewNavigator
        internal set

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d(TAG, "onPageStarted $url")
        state.loadingState = Loading(0.0f)
        state.errorsForCurrentRequest.clear()
        state.pageTitle = null
        state.isError = false
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "onPageFinished $url")
        if (url != "about:blank") {
            state.loadingState = Finished
        }
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
        Log.i(TAG, "doUpdateVisitedHistory $url, isReload: $isReload")

        if (url != null && !isReload && !url.startsWith("file:///")) {
            if (navigator.isGoingBack) {
                navigator.isGoingBack = false
            } else if (!state.lastLoadedUrl.isNullOrEmpty() && state.lastLoadedUrl != "about:blank") {
                state.lastLoadedUrl?.let { lastLoadedUrl ->
                    navigator.backQueue.addLast(lastLoadedUrl)
                }
            }
            navigator.filterBackQueue(url)
            state.lastLoadedUrl = url
        }

        navigator.canGoBack = navigator.backQueue.isNotEmpty()
        navigator.canGoForward = view.canGoForward()
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        Log.i(TAG, "onReceivedError ${request?.url} - ${error?.description}")
        if (request?.isForMainFrame == true) {
            Log.i(TAG, "Error received ${request.url} - ${error?.description}")
            state.isError = true
        }
        if (error != null) {
            state.errorsForCurrentRequest.add(WebViewError(request, error))
        }
    }
}

class CustomWebChromeClient(private val context: Context) : WebChromeClient() {
    lateinit var state: WebViewState
        internal set

    override fun onReceivedTitle(view: WebView, title: String?) {
        super.onReceivedTitle(view, title)
        state.pageTitle = title
    }

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        if (state.loadingState is Finished) return
        state.loadingState = Loading(newProgress / 100.0f)
    }

    override fun onShowFileChooser(
        webView: WebView?,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        val activity = context as MainActivity
        activity.uploadMessage = filePathCallback
        //activity.fileUploadContract.type = fileChooserParams.acceptTypes[0]
        val contract = activity.fileUpload.contract as FileUploadContract
        contract.type = getMimeType(fileChooserParams.acceptTypes)
        activity.fileUpload.launch(123)

        return true
    }

    private fun getMimeType(acceptTypes: Array<String>): String {
        for (type in acceptTypes) {
            return when (type) {
                ".json" -> "application/json"
                ".css" -> "text/css"
                else -> continue
            }
        }
        return "application/octet-stream"
    }
}

sealed class WebContent {
    data class Url(
        val url: String,
        val additionalHttpHeaders: Map<String, String> = emptyMap(),
    ) : WebContent()

    data class Data(
        val data: String,
        val baseUrl: String? = null,
        val encoding: String = "utf-8",
        val mimeType: String? = null,
        val historyUrl: String? = null
    ) : WebContent()

    data class Post(
        val url: String,
        val postData: ByteArray
    ) : WebContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Post

            if (url != other.url) return false
            if (!postData.contentEquals(other.postData)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = url.hashCode()
            result = 31 * result + postData.contentHashCode()
            return result
        }
    }

    data object NavigatorOnly : WebContent()
}

internal fun WebContent.withUrl(url: String) = when (this) {
    is WebContent.Url -> copy(url = url)
    else -> WebContent.Url(url)
}

/**
 * Sealed class for constraining possible loading states.
 * See [Loading] and [Finished].
 */
sealed class LoadingState {
    /**
     * Describes a WebView that has not yet loaded for the first time.
     */
    data object Initializing : LoadingState()

    /**
     * Describes a webview between `onPageStarted` and `onPageFinished` events, contains a
     * [progress] property which is updated by the webview.
     */
    data class Loading(val progress: Float) : LoadingState()

    /**
     * Describes a webview that has finished loading content.
     */
    data object Finished : LoadingState()
}

/**
 * A state holder to hold the state for the WebView. In most cases this will be remembered
 * using the rememberWebViewState(uri) function.
 */
@Stable
class WebViewState(webContent: WebContent) {
    var lastLoadedUrl: String? by mutableStateOf(null)
        internal set

    /**
     *  The content being loaded by the WebView
     */
    var content: WebContent by mutableStateOf(webContent)

    /**
     * Whether the WebView is currently [LoadingState.Loading] data in its main frame (along with
     * progress) or the data loading has [LoadingState.Finished]. See [LoadingState]
     */
    var loadingState: LoadingState by mutableStateOf(LoadingState.Initializing)
        internal set

    /**
     * Whether the webview is currently loading data in its main frame
     */
    val isLoading: Boolean
        get() = loadingState !is Finished

    /**
     * The title received from the loaded content of the current page
     */
    var pageTitle: String? by mutableStateOf(null)
        internal set

    var isError: Boolean by mutableStateOf(false)
        internal set

    /**
     * A list for errors captured in the last load. Reset when a new page is loaded.
     * Errors could be from any resource (iframe, image, etc.), not just for the main page.
     * For more fine grained control use the OnError callback of the WebView.
     */
    val errorsForCurrentRequest: SnapshotStateList<WebViewError> = mutableStateListOf()

    /**
     * The saved view state from when the view was destroyed last. To restore state,
     * use the navigator and only call loadUrl if the bundle is null.
     * See WebViewSaveStateSample.
     */
    var viewState: Bundle? = null
        internal set

    // We need access to this in the state saver. An internal DisposableEffect or AndroidView
    // onDestroy is called after the state saver and so can't be used.
    internal var webView by mutableStateOf<WebView?>(null)
}

/**
 * A wrapper class to hold errors from the WebView.
 */
@Immutable
data class WebViewError(
    /**
     * The request the error came from.
     */
    val request: WebResourceRequest?,
    /**
     * The error that was reported.
     */
    val error: WebResourceError
)

/**
 * Download listener for the WebView.
 */
fun downloadListener(
    device: Device,
    url: String,
    contentDisposition: String,
    mimetype: String,
    context: Context,
    onDownloadSuccess: () -> Unit
) {
    // Devices older than Android 10 (API 29) would need special permission handling for
    // MediaStore.Downloads. I decided to not support it for now for simplicity.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        Toast.makeText(
            context,
            context.getString(R.string.download_not_supported_old_android),
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val uri = url.toUri()

    when (uri.scheme) {
        "http", "https" -> {
            handleHttpDownload(
                context,
                url,
                contentDisposition,
                mimetype,
                device,
                onDownloadSuccess
            )
        }

        "data" -> {
            handleDataUriDownload(context, url, mimetype, device, onDownloadSuccess)
        }

        else -> {
            Log.w(TAG, "Unsupported download scheme: ${uri.scheme}")
            Toast.makeText(
                context,
                context.getString(R.string.download_scheme_not_supported, uri.scheme),
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/**
 * Helper function to handle HTTP/HTTPS download requests
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun handleHttpDownload(
    context: Context,
    url: String,
    contentDisposition: String,
    mimetype: String,
    device: Device,
    onSuccess: () -> Unit
) {
    val request = DownloadManager.Request(url.toUri())
    request.setNotificationVisibility(
        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
    )

    val fileName = createWledFilename(device, url, contentDisposition, mimetype)

    request.setDestinationInExternalPublicDir(
        Environment.DIRECTORY_DOWNLOADS,
        fileName
    )

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager?
    try {
        val downloadId = downloadManager?.enqueue(request) ?: return
        val downloadingToast = Toast.makeText(
            context,
            context.getString(R.string.downloading_file, fileName),
            Toast.LENGTH_SHORT
        )
        downloadingToast.show()
        // Listen for the "Download Complete" event to show the success message
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    downloadingToast.cancel()
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)

                    if (cursor != null && cursor.moveToFirst()) {
                        val status =
                            cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))

                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // Only trigger the snackbar if it actually worked
                            onSuccess()
                        } else {
                            // Handle failure
                            val reason =
                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                            val failureMessage = when (reason) {
                                DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Device not found"
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
                                DownloadManager.ERROR_FILE_ERROR -> "File error"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP Data error"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient space"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP code"
                                else -> "Unknown error"
                            }

                            Toast.makeText(
                                context,
                                context.getString(R.string.download_failed, failureMessage),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    cursor?.close()
                }
            }
        }

        // Register the receiver using the Application Context to avoid leaks if the Activity is closed
        ContextCompat.registerReceiver(
            context.applicationContext,
            onComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )
    } catch (e: Exception) {
        Log.e(TAG, "DownloadManager failed: ${e.message}")
        Toast.makeText(
            context,
            context.getString(R.string.download_failed, e.message ?: "Unknown error"),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Helper function to handle 'data:' URIs manually
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun handleDataUriDownload(
    context: Context,
    url: String,
    mimetype: String,
    device: Device,
    onSuccess: () -> Unit
) {
    try {
        // Robust data URI parsing
        val commaIndex = url.indexOf(",")
        if (commaIndex == -1) {
            Log.e(TAG, "Invalid data URI format")
            Toast.makeText(
                context,
                context.getString(R.string.invalid_data_uri),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // Check if it is base64
        val metadata = url.take(commaIndex)
        if (!metadata.contains("base64")) {
            Log.w(TAG, "Non-base64 data URIs are not currently supported")
            Toast.makeText(
                context,
                context.getString(R.string.non_base64_not_supported),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val base64Data = url.substring(commaIndex + 1)
        val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)

        // Use helper to generate consistent filename
        val fileName = createWledFilename(device, null, null, mimetype)

        // Save using MediaStore
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimetype)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri).use { it?.write(decodedBytes) }
            onSuccess()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.file_creation_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Data URI download failed", e)
        Toast.makeText(
            context,
            context.getString(R.string.error_saving_file),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Shared helper to generate consistent filenames for WLED exports.
 * Removes illegal characters but keeps spaces for readability.
 */
private fun createWledFilename(
    device: Device,
    url: String?,
    contentDisposition: String?,
    mimetype: String
): String {
    val formatter = SimpleDateFormat("yyyyMMdd", Locale.US)
    val currentDate = formatter.format(Date())

    // Sanitize device name: Allow letters, numbers, spaces, underscores, and hyphens.
    // Replace everything else with underscore to ensure filesystem compatibility.
    val sanitizedDeviceName = device.originalName.replace(Regex("[^a-zA-Z0-9 \\-_]"), "_")

    val extension = when {
        url != null -> URLUtil.guessFileName(url, contentDisposition, mimetype)
            .substringAfterLast('.', "json")

        mimetype.contains("json") -> "json"
        else -> "txt"
    }

    // If URLUtil guessed the whole name including backup, use it, otherwise construct our standard format
    val baseName = if (url != null && contentDisposition != null) {
        URLUtil.guessFileName(url, contentDisposition, mimetype)
    } else {
        "wled_backup.$extension"
    }

    // Result example: "My_Light_20231025_wled_backup.json"
    // Note: If baseName already has the extension, we are good.
    return "${sanitizedDeviceName}_${currentDate}_$baseName"
}

/**
 * Logic to open the system Downloads folder.
 */
fun openDownloadedFile(context: Context) {
    try {
        val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open Downloads", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Allows control over the navigation of a WebView from outside the composable. E.g. for performing
 * a back navigation in response to the user clicking the "up" button in a TopAppBar.
 *
 * @see [rememberWebViewNavigator]
 */
@Stable
class WebViewNavigator(private val coroutineScope: CoroutineScope) {

    init {
        Log.d(TAG, "WebViewNavigator init")
    }

    internal var backQueue = ArrayDeque<String>(5)
        set(value) {
            canGoBack = value.isNotEmpty()
            field = value
        }

    private sealed interface NavigationEvent {
        data object Back : NavigationEvent
        data object Forward : NavigationEvent
        data object Reload : NavigationEvent
        data object StopLoading : NavigationEvent

        data class LoadUrl(
            val url: String,
            val additionalHttpHeaders: Map<String, String> = emptyMap()
        ) : NavigationEvent

        data class LoadHtml(
            val html: String,
            val baseUrl: String? = null,
            val mimeType: String? = null,
            val encoding: String? = "utf-8",
            val historyUrl: String? = null
        ) : NavigationEvent

        data class PostUrl(
            val url: String,
            val postData: ByteArray
        ) : NavigationEvent {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as PostUrl

                if (url != other.url) return false
                if (!postData.contentEquals(other.postData)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = url.hashCode()
                result = 31 * result + postData.contentHashCode()
                return result
            }
        }
    }

    private val navigationEvents: MutableSharedFlow<NavigationEvent> = MutableSharedFlow(replay = 1)

    // Use Dispatchers.Main to ensure that the webview methods are called on UI thread
    internal suspend fun WebView.handleNavigationEvents(): Nothing = withContext(Dispatchers.Main) {
        navigationEvents.collect { event ->
            when (event) {
                is NavigationEvent.Back -> goBackLogic()
                is NavigationEvent.Forward -> goForward()
                is NavigationEvent.Reload -> reload()
                is NavigationEvent.StopLoading -> stopLoading()
                is NavigationEvent.LoadHtml -> loadDataWithBaseURL(
                    event.baseUrl,
                    event.html,
                    event.mimeType,
                    event.encoding,
                    event.historyUrl
                )

                is NavigationEvent.LoadUrl -> {
                    loadUrl(event.url, event.additionalHttpHeaders)
                }

                is NavigationEvent.PostUrl -> {
                    postUrl(event.url, event.postData)
                }
            }
        }
    }

    var isGoingBack: Boolean by mutableStateOf(false)
        internal set

    /**
     * True when the web view is able to navigate backwards, false otherwise.
     */
    var canGoBack: Boolean by mutableStateOf(false)
        internal set

    /**
     * True when the web view is able to navigate forwards, false otherwise.
     */
    var canGoForward: Boolean by mutableStateOf(false)
        internal set

    fun goBackLogic(): Boolean {
        if (canGoBack && backQueue.isNotEmpty()) {
            val backUrl = backQueue.removeLast()
            loadUrl(backUrl)
            isGoingBack = true
            return true
        }
        Log.i(TAG, "Can't go back")
        return false
    }

    internal fun reset() {
        Log.d(TAG, "Resetting back queue")
        backQueue.clear()
        canGoBack = false
        canGoForward = false
    }

    /**
     * Checks if the current url already exists in the back queue. If it does, pop all items until
     * the duplicated current url.
     *
     * @param currentUrl
     */
    internal fun filterBackQueue(currentUrl: String) {
        Log.i(TAG, "== Starting filter ========")
        Log.i(TAG, "Current Url: $currentUrl")
        Log.i(TAG, backQueue.count().toString())
        Log.i(TAG, backQueue.toString())
        var i = backQueue.size
        for (url in backQueue.asReversed()) {
            i--
            if (url == currentUrl) {
                backQueue.subList(i, backQueue.size).clear()
                Log.i(TAG, "Removing up to $i")
                Log.i(TAG, backQueue.toString())
                return
            }
        }
    }

    fun loadUrl(url: String, additionalHttpHeaders: Map<String, String> = emptyMap()) {
        coroutineScope.launch {
            navigationEvents.emit(
                NavigationEvent.LoadUrl(
                    url,
                    additionalHttpHeaders
                )
            )
        }
    }

    fun loadHtml(
        html: String,
        baseUrl: String? = null,
        mimeType: String? = null,
        encoding: String? = "utf-8",
        historyUrl: String? = null
    ) {
        coroutineScope.launch {
            navigationEvents.emit(
                NavigationEvent.LoadHtml(
                    html,
                    baseUrl,
                    mimeType,
                    encoding,
                    historyUrl
                )
            )
        }
    }

    fun postUrl(
        url: String,
        postData: ByteArray
    ) {
        coroutineScope.launch {
            navigationEvents.emit(
                NavigationEvent.PostUrl(
                    url,
                    postData
                )
            )
        }
    }

    /**
     * Navigates the webview back to the previous page.
     */
    fun navigateBack() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Back) }
    }

    /**
     * Navigates the webview forward after going back from a page.
     */
    fun navigateForward() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Forward) }
    }

    /**
     * Reloads the current page in the webview.
     */
    fun reload() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.Reload) }
    }

    /**
     * Stops the current page load (if one is loading).
     */
    fun stopLoading() {
        coroutineScope.launch { navigationEvents.emit(NavigationEvent.StopLoading) }
    }
}

/**
 * Creates and remembers a [WebViewNavigator] using the default [CoroutineScope] or a provided
 * override.
 */
@Composable
fun rememberWebViewNavigator(
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): WebViewNavigator = remember(coroutineScope) { WebViewNavigator(coroutineScope) }

/**
 * Creates a WebView state that is remembered across Compositions.
 *
 * @param url The url to load in the WebView
 * @param additionalHttpHeaders Optional, additional HTTP headers that are passed to [WebView.loadUrl].
 *                              Note that these headers are used for all subsequent requests of the WebView.
 */

@Composable
fun rememberWebViewState(
    url: String,
    additionalHttpHeaders: Map<String, String> = emptyMap()
): WebViewState =
// Rather than using .apply {} here we will recreate the state, this prevents
    // a recomposition loop when the webview updates the url itself.
    remember {
        WebViewState(
            WebContent.Url(
                url = url,
                additionalHttpHeaders = additionalHttpHeaders
            )
        )
    }.apply {
        this.content = WebContent.Url(
            url = url,
            additionalHttpHeaders = additionalHttpHeaders
        )
    }

/**
 * Creates a WebView state that is remembered across Compositions.
 *
 * @param data The uri to load in the WebView
 */
@Composable
fun rememberWebViewStateWithHTMLData(
    data: String,
    baseUrl: String? = null,
    encoding: String = "utf-8",
    mimeType: String? = null,
    historyUrl: String? = null
): WebViewState =
    remember {
        WebViewState(WebContent.Data(data, baseUrl, encoding, mimeType, historyUrl))
    }.apply {
        this.content = WebContent.Data(
            data, baseUrl, encoding, mimeType, historyUrl
        )
    }

/**
 * Creates a WebView state that is remembered across Compositions.
 *
 * @param url The url to load in the WebView
 * @param postData The data to be posted to the WebView with the url
 */
@Composable
fun rememberWebViewState(
    url: String,
    postData: ByteArray
): WebViewState =
// Rather than using .apply {} here we will recreate the state, this prevents
    // a recomposition loop when the webview updates the url itself.
    remember {
        WebViewState(
            WebContent.Post(
                url = url,
                postData = postData
            )
        )
    }.apply {
        this.content = WebContent.Post(
            url = url,
            postData = postData
        )
    }

/**
 * Creates a WebView state that is remembered across Compositions and saved
 * across activity recreation.
 * When using saved state, you cannot change the URL via recomposition. The only way to load
 * a URL is via a WebViewNavigator.
 *
 * @param data The uri to load in the WebView
 * @sample com.google.accompanist.sample.webview.WebViewSaveStateSample
 */
@Composable
fun rememberSaveableWebViewState(): WebViewState =
    rememberSaveable(saver = WebStateSaver) {
        WebViewState(WebContent.NavigatorOnly)
    }

val WebStateSaver: Saver<WebViewState, Any> = run {
    val pageTitleKey = "pagetitle"
    val lastLoadedUrlKey = "lastloaded"
    val isErrorKey = "isError"
    val stateBundle = "bundle"

    mapSaver(
        save = {
            val viewState = Bundle().apply { it.webView?.saveState(this) }
            mapOf(
                pageTitleKey to it.pageTitle,
                lastLoadedUrlKey to it.lastLoadedUrl,
                isErrorKey to it.isError,
                stateBundle to viewState
            )
        },
        restore = {
            WebViewState(WebContent.NavigatorOnly).apply {
                this.pageTitle = it[pageTitleKey] as String?
                this.lastLoadedUrl = it[lastLoadedUrlKey] as String?
                this.isError = (it[isErrorKey] as Boolean?) ?: false
                this.viewState = it[stateBundle] as Bundle?
            }
        }
    )
}