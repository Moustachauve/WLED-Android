package ca.cgagnier.wlednativeandroid.ui.components

import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ca.cgagnier.wlednativeandroid.WebViewHolder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebViewViewModelTest {

    private fun createViewModel(store: ViewModelStore = ViewModelStore()): WebViewViewModel {
        val factory = viewModelFactory {
            initializer { WebViewViewModel(mockk<WebViewHolder>(relaxed = true)) }
        }
        return ViewModelProvider.create(store, factory)[WebViewViewModel::class]
    }

    @Test
    fun `onReceiveFileUploadResult delivers selected URIs to pending callback once`() {
        val viewModel = createViewModel()
        val expectedUris = arrayOf(mockk<Uri>())
        val callback = RecordingFileUploadCallback()

        viewModel.onStartFileUpload(callback)
        viewModel.onReceiveFileUploadResult(expectedUris)
        viewModel.onReceiveFileUploadResult(null)

        assertEquals(1, callback.calls.size)
        assertArrayEquals(expectedUris, callback.calls.single())
    }

    @Test
    fun `onStartFileUpload cancels previous pending callback when a new upload starts`() {
        val viewModel = createViewModel()
        val firstCallback = RecordingFileUploadCallback()

        viewModel.onStartFileUpload(firstCallback)
        viewModel.onStartFileUpload(RecordingFileUploadCallback())

        assertEquals(listOf<Array<Uri>?>(null), firstCallback.calls)
    }

    @Test
    fun `onCleared cancels pending file upload callback`() {
        val store = ViewModelStore()
        val viewModel = createViewModel(store)
        val callback = RecordingFileUploadCallback()

        viewModel.onStartFileUpload(callback)
        store.clear()

        assertEquals(listOf<Array<Uri>?>(null), callback.calls)
    }
}
