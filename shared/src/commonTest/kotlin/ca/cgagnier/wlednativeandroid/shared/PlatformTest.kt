package ca.cgagnier.wlednativeandroid.shared

import kotlin.test.Test
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun testPlatformNameIsNotEmpty() {
        assertTrue(getPlatformName().isNotEmpty())
    }

    @Test
    fun testCurrentTimeMillisIsPositive() {
        assertTrue(currentTimeMillis() > 0)
    }
}
