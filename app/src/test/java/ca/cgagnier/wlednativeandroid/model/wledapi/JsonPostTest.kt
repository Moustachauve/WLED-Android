package ca.cgagnier.wlednativeandroid.model.wledapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPostTest {

    @Test
    fun `test JsonPost defaults`() {
        val jsonPost = JsonPost()
        assertNull(jsonPost.isOn)
        assertNull(jsonPost.brightness)
        assertTrue(jsonPost.verbose)
    }

    @Test
    fun `test JsonPost custom values`() {
        val jsonPost = JsonPost(isOn = true, brightness = 128, verbose = false)
        assertEquals(true, jsonPost.isOn)
        assertEquals(128, jsonPost.brightness)
        assertEquals(false, jsonPost.verbose)
    }
}
