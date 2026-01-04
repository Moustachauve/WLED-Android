package ca.cgagnier.wlednativeandroid.service.api

sealed class DownloadState {
    data class Downloading(val progress: Int) : DownloadState()
    data object Finished : DownloadState()
    data class Failed(val error: Throwable? = null) : DownloadState()
}