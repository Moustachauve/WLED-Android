package ca.cgagnier.wlednativeandroid.shared

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedModuleLinkTest {
    @Test
    fun `shared module is accessible and returns expected platform`() {
        assertEquals("Android", getPlatformName())
    }
}
