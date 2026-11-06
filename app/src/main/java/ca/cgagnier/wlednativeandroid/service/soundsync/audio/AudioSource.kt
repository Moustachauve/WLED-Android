package ca.cgagnier.wlednativeandroid.service.soundsync.audio

import kotlinx.coroutines.flow.StateFlow

/**
 * Audio source for sound sync. Implementations capture audio and emit
 * normalized PCM samples (floats in [-1.0, 1.0]) via the [onAudioData] callback.
 */
interface AudioSource {
    /** Sample rate in Hz of the emitted audio data. */
    val sampleRate: Int

    /** Whether the source is currently capturing audio. */
    val isRunning: StateFlow<Boolean>

    /** Returns `true` if the required audio recording permission is granted. */
    fun hasPermission(): Boolean

    /**
     * Starts audio capture. The [onAudioData] callback is invoked with chunks
     * of normalized PCM samples as they become available.
     *
     * @param onAudioData Callback invoked on a background thread with audio sample chunks.
     * @return `true` if the source started successfully.
     */
    fun start(onAudioData: (FloatArray) -> Unit): Boolean

    /** Stops audio capture and releases active recording resources. */
    fun stop()

    /** Permanently stops capture and cancels any internal coroutine scopes. */
    fun destroy()
}
