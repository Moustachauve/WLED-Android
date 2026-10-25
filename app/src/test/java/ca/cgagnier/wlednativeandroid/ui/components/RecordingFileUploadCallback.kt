package ca.cgagnier.wlednativeandroid.ui.components

import android.net.Uri
import android.webkit.ValueCallback

internal class RecordingFileUploadCallback : ValueCallback<Array<Uri>> {
    val calls = mutableListOf<Array<Uri>?>()

    override fun onReceiveValue(value: Array<Uri>?) {
        calls.add(value)
    }
}
