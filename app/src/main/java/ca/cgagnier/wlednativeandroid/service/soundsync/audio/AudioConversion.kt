package ca.cgagnier.wlednativeandroid.service.soundsync.audio

private const val PCM16_MAX_VALUE = 32768.0f
private const val BYTES_PER_SAMPLE_16BIT = 2
private const val BYTE_MASK = 0xFF
private const val BYTE_SHIFT = 8
private const val UNSIGNED_8_CENTER = 128
private const val UNSIGNED_8_MAX = 128.0f

/**
 * Converts 16-bit signed PCM samples to normalized floats in [-1.0, 1.0].
 */
fun pcm16ToFloat(pcm: ShortArray): FloatArray {
    if (pcm.isEmpty()) return FloatArray(0)
    val result = FloatArray(pcm.size)
    for (i in pcm.indices) {
        result[i] = pcm[i] / PCM16_MAX_VALUE
    }
    return result
}

/**
 * Converts 16-bit signed PCM from a byte buffer (little-endian) to normalized floats.
 */
fun pcm16BytesToFloat(bytes: ByteArray, length: Int): FloatArray {
    val sampleCount = length / BYTES_PER_SAMPLE_16BIT
    val result = FloatArray(sampleCount)
    for (i in 0 until sampleCount) {
        val lo = bytes[i * BYTES_PER_SAMPLE_16BIT].toInt() and BYTE_MASK
        val hi = bytes[i * BYTES_PER_SAMPLE_16BIT + 1].toInt() // Sign-extended
        val sample = (hi shl BYTE_SHIFT) or lo
        result[i] = sample / PCM16_MAX_VALUE
    }
    return result
}

/**
 * Converts 8-bit unsigned PCM samples (0-255, centered at 128) to normalized
 * floats in [-1.0, 1.0]. Used for Android Visualizer waveform data.
 */
fun unsigned8ToFloat(bytes: ByteArray): FloatArray {
    if (bytes.isEmpty()) return FloatArray(0)
    val result = FloatArray(bytes.size)
    for (i in bytes.indices) {
        result[i] = ((bytes[i].toInt() and BYTE_MASK) - UNSIGNED_8_CENTER) / UNSIGNED_8_MAX
    }
    return result
}
