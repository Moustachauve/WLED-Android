package ca.cgagnier.wlednativeandroid.ui.components

import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CustomWebViewClientTest {

    private fun createClientWithLoadingState(): Pair<CustomWebViewClient, WebViewState> {
        val state = WebViewState(WebContent.NavigatorOnly).apply {
            loadingState = LoadingState.Loading(0.2f)
        }
        val client = CustomWebViewClient().apply {
            this.state = state
        }
        return client to state
    }

    @Test
    fun `onReceivedError marks loading finished and sets isError for main frame error`() {
        val (client, state) = createClientWithLoadingState()
        val request = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns true
        }
        val error = mockk<WebResourceError>(relaxed = true)

        client.onReceivedError(view = null, request = request, error = error)

        assertTrue(state.isError)
        assertEquals(LoadingState.Finished, state.loadingState)
    }

    @Test
    fun `onReceivedError preserves loading state for subframe resource error`() {
        val (client, state) = createClientWithLoadingState()
        val request = mockk<WebResourceRequest>(relaxed = true) {
            every { isForMainFrame } returns false
        }
        val error = mockk<WebResourceError>(relaxed = true)

        client.onReceivedError(view = null, request = request, error = error)

        assertFalse(state.isError)
        assertEquals(LoadingState.Loading(0.2f), state.loadingState)
    }
}
