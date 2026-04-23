package ca.cgagnier.wlednativeandroid.util

import android.content.ActivityNotFoundException
import androidx.compose.ui.platform.UriHandler
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UriUtilsTest {

    @Test
    fun `openUriSafely calls openUri with correct uri`() {
        val mockUriHandler = mockk<UriHandler>(relaxed = true)
        val testUri = "https://example.com"

        mockUriHandler.openUriSafely(testUri)

        verify { mockUriHandler.openUri(testUri) }
    }

    @Test
    fun `openUriSafely handles IllegalArgumentException without crashing`() {
        val mockUriHandler = mockk<UriHandler>()
        val testUri = "https://example.com"
        every { mockUriHandler.openUri(any()) } throws IllegalArgumentException("Invalid URI")

        // Should not throw
        mockUriHandler.openUriSafely(testUri)

        verify { mockUriHandler.openUri(testUri) }
    }

    @Test
    fun `openUriSafely handles ActivityNotFoundException without crashing`() {
        val mockUriHandler = mockk<UriHandler>()
        val testUri = "https://example.com"
        every { mockUriHandler.openUri(any()) } throws ActivityNotFoundException("No browser found")

        // Should not throw
        mockUriHandler.openUriSafely(testUri)

        verify { mockUriHandler.openUri(testUri) }
    }
}
