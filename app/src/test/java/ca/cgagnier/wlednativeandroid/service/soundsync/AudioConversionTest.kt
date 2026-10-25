package ca.cgagnier.wlednativeandroid.service.soundsync

import ca.cgagnier.wlednativeandroid.service.soundsync.audio.pcm16ToFloat
import ca.cgagnier.wlednativeandroid.service.soundsync.audio.unsigned8ToFloat
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AudioConversionTest {

    @Test
    fun pcm16ToFloat_convertsZeroToZero() {
        val pcm = shortArrayOf(0, 0, 0)
        val result = pcm16ToFloat(pcm)
        assertEquals(0f, result[0], 0.0001f)
        assertEquals(0f, result[1], 0.0001f)
        assertEquals(0f, result[2], 0.0001f)
    }

    @Test
    fun pcm16ToFloat_convertsMaxPositiveToNearOne() {
        val pcm = shortArrayOf(Short.MAX_VALUE)
        val result = pcm16ToFloat(pcm)
        assertEquals(1.0f, result[0], 0.001f)
    }

    @Test
    fun pcm16ToFloat_convertsMaxNegativeToNearMinusOne() {
        val pcm = shortArrayOf(Short.MIN_VALUE)
        val result = pcm16ToFloat(pcm)
        assertEquals(-1.0f, result[0], 0.001f)
    }

    @Test
    fun pcm16ToFloat_convertsHalfwayToHalf() {
        val pcm = shortArrayOf(16384) // ~0.5 * 32768
        val result = pcm16ToFloat(pcm)
        assertEquals(0.5f, result[0], 0.01f)
    }

    @Test
    fun pcm16ToFloat_preservesLength() {
        val pcm = shortArrayOf(100, -200, 300, -400, 500)
        val result = pcm16ToFloat(pcm)
        assertEquals(pcm.size, result.size)
    }

    @Test
    fun pcm16ToFloat_emptyInput_returnsEmptyOutput() {
        val result = pcm16ToFloat(shortArrayOf())
        assertEquals(0, result.size)
    }

    @Test
    fun unsigned8ToFloat_convertsMidpointToZero() {
        val bytes = byteArrayOf(128.toByte())
        val result = unsigned8ToFloat(bytes)
        assertEquals(0f, result[0], 0.01f)
    }

    @Test
    fun unsigned8ToFloat_convertsZeroToMinusOne() {
        val bytes = byteArrayOf(0)
        val result = unsigned8ToFloat(bytes)
        assertEquals(-1.0f, result[0], 0.01f)
    }

    @Test
    fun unsigned8ToFloat_convertsMaxToNearOne() {
        val bytes = byteArrayOf(255.toByte())
        val result = unsigned8ToFloat(bytes)
        assertEquals(127f / 128f, result[0], 0.01f)
    }

    @Test
    fun unsigned8ToFloat_preservesLength() {
        val bytes = byteArrayOf(100, -128, -56, 50) // -128 = 128 unsigned, -56 = 200 unsigned
        val result = unsigned8ToFloat(bytes)
        assertEquals(bytes.size, result.size)
    }

    @Test
    fun unsigned8ToFloat_emptyInput_returnsEmptyOutput() {
        val result = unsigned8ToFloat(byteArrayOf())
        assertEquals(0, result.size)
    }
}
