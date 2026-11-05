package ca.cgagnier.wlednativeandroid.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient.FileChooserParams
import ca.cgagnier.wlednativeandroid.WebViewHolder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomWebChromeClientTest {

    private fun createViewModel(): WebViewViewModel = WebViewViewModel(mockk<WebViewHolder>(relaxed = true))

    private fun fileChooserParams(type: String? = null): Pair<FileChooserParams, Intent> {
        val intent = mockk<Intent>(relaxed = true) {
            every { this@mockk.type } returns type
        }
        val params = mockk<FileChooserParams> {
            every { createIntent() } returns intent
        }
        return params to intent
    }

    @Test
    fun `onShowFileChooser returns false without invoking callback when no handler is registered`() {
        val chromeClient = CustomWebChromeClient()
        val callback = RecordingFileUploadCallback()

        val handled = chromeClient.onShowFileChooser(
            webView = null,
            filePathCallback = callback,
            fileChooserParams = mockk(relaxed = true),
        )

        assertFalse(handled)
        assertTrue(callback.calls.isEmpty())
    }

    @Test
    fun `launchFileChooser configures wildcard mime type for non-image inputs`() {
        val viewModel = createViewModel()
        val (params, intent) = fileChooserParams(type = "application/json")
        var launchedIntent: Intent? = null

        val handled = launchFileChooser(
            webViewViewModel = viewModel,
            filePathCallback = ValueCallback {},
            fileChooserParams = params,
        ) { launchedIntent = it }

        assertTrue(handled)
        assertSame(intent, launchedIntent)
        verify { intent.type = "*/*" }
        verify { intent.removeExtra(Intent.EXTRA_MIME_TYPES) }
    }

    @Test
    fun `launchFileChooser preserves image mime type for image inputs`() {
        val viewModel = createViewModel()
        val (params, intent) = fileChooserParams(type = "image/*")
        var launchedIntent: Intent? = null

        val handled = launchFileChooser(
            webViewViewModel = viewModel,
            filePathCallback = ValueCallback {},
            fileChooserParams = params,
        ) { launchedIntent = it }

        assertTrue(handled)
        assertSame(intent, launchedIntent)
        verify(exactly = 0) { intent.type = any() }
        verify(exactly = 0) { intent.removeExtra(Intent.EXTRA_MIME_TYPES) }
    }

    @Test
    fun `launchFileChooser cancels callback and returns true when ActivityNotFoundException is thrown`() {
        val viewModel = createViewModel()
        val (params, _) = fileChooserParams()
        val callback = RecordingFileUploadCallback()

        val handled = launchFileChooser(
            webViewViewModel = viewModel,
            filePathCallback = callback,
            fileChooserParams = params,
        ) {
            throw ActivityNotFoundException("No file picker installed")
        }

        assertTrue(handled)
        assertEquals(listOf<Array<Uri>?>(null), callback.calls)
    }
}
