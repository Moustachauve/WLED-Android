package ca.cgagnier.wlednativeandroid.util

import kotlin.test.Test
import kotlin.test.assertEquals

class BrightnessUtilsTest {

    // --- brightnessToPercent tests ---

    @Test
    fun brightnessToPercent_withZero_returnsZeroPercent() {
        assertEquals(0f, brightnessToPercent(0), 0.01f)
    }

    @Test
    fun brightnessToPercent_with255_returns100Percent() {
        assertEquals(100f, brightnessToPercent(255), 0.01f)
    }

    @Test
    fun brightnessToPercent_with128_returnsApproximately50Percent() {
        // 128/255 * 100 ≈ 50.2%
        assertEquals(50.2f, brightnessToPercent(128), 0.1f)
    }

    @Test
    fun brightnessToPercent_withOne_returnsApproximatelyZeroPointFourPercent() {
        // 1/255 * 100 ≈ 0.39%
        assertEquals(0.39f, brightnessToPercent(1), 0.1f)
    }

    @Test
    fun brightnessToPercent_clampsNegativeValuesToZero() {
        assertEquals(0f, brightnessToPercent(-10), 0.01f)
    }

    @Test
    fun brightnessToPercent_clampsValuesAbove255To100() {
        assertEquals(100f, brightnessToPercent(300), 0.01f)
    }

    // --- percentToBrightness tests ---

    @Test
    fun percentToBrightness_withZeroPercent_returnsZero() {
        assertEquals(0, percentToBrightness(0f))
    }

    @Test
    fun percentToBrightness_with100Percent_returns255() {
        assertEquals(255, percentToBrightness(100f))
    }

    @Test
    fun percentToBrightness_with50Percent_returns127() {
        // 50/100 * 255 = 127.5 → 127
        assertEquals(127, percentToBrightness(50f))
    }

    @Test
    fun percentToBrightness_withOnePercent_returnsTwo() {
        // 1/100 * 255 = 2.55 → 2
        assertEquals(2, percentToBrightness(1f))
    }

    @Test
    fun percentToBrightness_clampsNegativeValuesToZero() {
        assertEquals(0, percentToBrightness(-10f))
    }

    @Test
    fun percentToBrightness_clampsValuesAbove100To255() {
        assertEquals(255, percentToBrightness(150f))
    }

    // --- Roundtrip tests ---

    @Test
    fun roundtrip_fromBrightnessToPercentAndBack_preservesApproximateValue() {
        val original = 200
        val percent = brightnessToPercent(original)
        val roundtrip = percentToBrightness(percent)
        // Allow for rounding differences
        assertEquals(original.toFloat(), roundtrip.toFloat(), 1f)
    }

    @Test
    fun roundtrip_fromPercentToBrightnessAndBack_preservesApproximateValue() {
        val original = 75f
        val brightness = percentToBrightness(original)
        val roundtrip = brightnessToPercent(brightness)
        // Allow for rounding differences
        assertEquals(original, roundtrip, 1f)
    }
}
