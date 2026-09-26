package ca.cgagnier.wlednativeandroid.model.wledapi

import com.diffplug.selfie.Selfie.expectSelfie
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonPostTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

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

    @Test
    fun `test JsonPost serialization omits null values`() {
        val jsonPost = JsonPost()
        val serialized = json.encodeToString(jsonPost)
        expectSelfie(serialized).toBe("""{"v":true}""")
    }

    @Test
    fun `test JsonPost serialization with all values set`() {
        val jsonPost = JsonPost(isOn = true, brightness = 200, verbose = false)
        val serialized = json.encodeToString(jsonPost)
        expectSelfie(serialized).toBe("{\"on\":true,\"bri\":200,\"v\":false}")
    }

    @Test
    fun `test JsonPost deserialization`() {
        val rawJson = """{"on":true,"bri":255,"v":false}"""
        val parsed = json.decodeFromString<JsonPost>(rawJson)
        expectSelfie(json.encodeToString(parsed)).toBe("""{"on":true,"bri":255,"v":false}""")
    }
}
