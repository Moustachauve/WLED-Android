package ca.cgagnier.wlednativeandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BrightnessUtilsTest {

    // --- brightnessToPercent tests ---

    @Test
    fun `brightnessToPercent with 0 returns 0 percent`() {
        assertEquals(0f, brightnessToPercent(0), 0.01f)
    }

    @Test
    fun `brightnessToPercent with 255 returns 100 percent`() {
        assertEquals(100f, brightnessToPercent(255), 0.01f)
    }

    @Test
    fun `brightnessToPercent with 128 returns approximately 50 percent`() {
        // 128/255 * 100 ≈ 50.2%
        assertEquals(50.2f, brightnessToPercent(128), 0.1f)
    }

    @Test
    fun `brightnessToPercent with 1 returns approximately 0_4 percent`() {
        // 1/255 * 100 ≈ 0.39%
        assertEquals(0.39f, brightnessToPercent(1), 0.1f)
    }

    @Test
    fun `brightnessToPercent clamps negative values to 0`() {
        assertEquals(0f, brightnessToPercent(-10), 0.01f)
    }

    @Test
    fun `brightnessToPercent clamps values above 255 to 100`() {
        assertEquals(100f, brightnessToPercent(300), 0.01f)
    }

    // --- percentToBrightness tests ---

    @Test
    fun `percentToBrightness with 0 percent returns 0`() {
        assertEquals(0, percentToBrightness(0f))
    }

    @Test
    fun `percentToBrightness with 100 percent returns 255`() {
        assertEquals(255, percentToBrightness(100f))
    }

    @Test
    fun `percentToBrightness with 50 percent returns 127`() {
        // 50/100 * 255 = 127.5 → 127
        assertEquals(127, percentToBrightness(50f))
    }

    @Test
    fun `percentToBrightness with 1 percent returns 2`() {
        // 1/100 * 255 = 2.55 → 2
        assertEquals(2, percentToBrightness(1f))
    }

    @Test
    fun `percentToBrightness clamps negative values to 0`() {
        assertEquals(0, percentToBrightness(-10f))
    }

    @Test
    fun `percentToBrightness clamps values above 100 to 255`() {
        assertEquals(255, percentToBrightness(150f))
    }

    // --- Roundtrip tests ---

    @Test
    fun `roundtrip from brightness to percent and back preserves approximate value`() {
        val original = 200
        val percent = brightnessToPercent(original)
        val roundtrip = percentToBrightness(percent)
        // Allow for rounding differences
        assertEquals(original.toFloat(), roundtrip.toFloat(), 1f)
    }

    @Test
    fun `roundtrip from percent to brightness and back preserves approximate value`() {
        val original = 75f
        val brightness = percentToBrightness(original)
        val roundtrip = brightnessToPercent(brightness)
        // Allow for rounding differences
        assertEquals(original, roundtrip, 1f)
    }
}
