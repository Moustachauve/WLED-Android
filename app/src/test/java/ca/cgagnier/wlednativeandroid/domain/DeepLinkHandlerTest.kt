package ca.cgagnier.wlednativeandroid.domain

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeepLinkHandlerTest {

    private lateinit var handler: DeepLinkHandler

    @Before
    fun setUp() {
        handler = DeepLinkHandler()
    }

    // --- parseUri tests ---

    @Test
    fun `parseUri with wled scheme and MAC address returns MacAddress`() {
        val uri = Uri.parse("wled://AABBCCDDEEFF")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with wled scheme and lowercase MAC address returns uppercase MacAddress`() {
        val uri = Uri.parse("wled://aabbccddeeff")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with wled scheme and mixed case MAC address returns uppercase MacAddress`() {
        val uri = Uri.parse("wled://AaBbCcDdEeFf")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.MacAddress)
        assertEquals("AABBCCDDEEFF", (result as DeepLink.MacAddress).mac)
    }

    @Test
    fun `parseUri with wled scheme and IPv4 address returns Address`() {
        val uri = Uri.parse("wled://192.168.1.50")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.Address)
        assertEquals("192.168.1.50", (result as DeepLink.Address).address)
    }

    @Test
    fun `parseUri with wled scheme and hostname returns Address`() {
        val uri = Uri.parse("wled://wled.local")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.Address)
        assertEquals("wled.local", (result as DeepLink.Address).address)
    }

    @Test
    fun `parseUri with wled scheme and complex hostname returns Address`() {
        val uri = Uri.parse("wled://my-wled-device.home.arpa")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.Address)
        assertEquals("my-wled-device.home.arpa", (result as DeepLink.Address).address)
    }

    @Test
    fun `parseUri with http scheme and AP mode IP returns ApMode`() {
        val uri = Uri.parse("http://4.3.2.1")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.ApMode)
    }

    @Test
    fun `parseUri with http scheme and AP mode IP with path returns ApMode`() {
        val uri = Uri.parse("http://4.3.2.1/edit")
        val result = handler.parseUri(uri)

        assertTrue(result is DeepLink.ApMode)
    }

    @Test
    fun `parseUri with http scheme and non-AP IP returns null`() {
        val uri = Uri.parse("http://192.168.1.50")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with https scheme returns null`() {
        val uri = Uri.parse("https://4.3.2.1")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with unsupported scheme returns null`() {
        val uri = Uri.parse("ftp://192.168.1.50")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    @Test
    fun `parseUri with null returns null`() {
        val result = handler.parseUri(null)

        assertNull(result)
    }

    @Test
    fun `parseUri with wled scheme and empty host returns null`() {
        val uri = Uri.parse("wled://")
        val result = handler.parseUri(uri)

        assertNull(result)
    }

    // --- parseIntent tests ---

    @Test
    fun `parseIntent with ACTION_VIEW and valid wled uri returns DeepLink`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("wled://192.168.1.50"))
        val result = handler.parseIntent(intent)

        assertTrue(result is DeepLink.Address)
    }

    @Test
    fun `parseIntent with ACTION_MAIN returns null`() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.data = Uri.parse("wled://192.168.1.50")
        val result = handler.parseIntent(intent)

        assertNull(result)
    }

    @Test
    fun `parseIntent with null intent returns null`() {
        val result = handler.parseIntent(null)

        assertNull(result)
    }

    @Test
    fun `parseIntent with ACTION_VIEW but no data returns null`() {
        val intent = Intent(Intent.ACTION_VIEW)
        val result = handler.parseIntent(intent)

        assertNull(result)
    }

    // --- isMacAddress tests ---

    @Test
    fun `isMacAddress with valid 12 hex chars returns true`() {
        assertTrue(handler.isMacAddress("AABBCCDDEEFF"))
    }

    @Test
    fun `isMacAddress with valid lowercase 12 hex chars returns true`() {
        assertTrue(handler.isMacAddress("aabbccddeeff"))
    }

    @Test
    fun `isMacAddress with valid mixed case 12 hex chars returns true`() {
        assertTrue(handler.isMacAddress("AaBbCcDdEeFf"))
    }

    @Test
    fun `isMacAddress with 11 chars returns false`() {
        assertFalse(handler.isMacAddress("AABBCCDDEE"))
    }

    @Test
    fun `isMacAddress with 13 chars returns false`() {
        assertFalse(handler.isMacAddress("AABBCCDDEEFFF"))
    }

    @Test
    fun `isMacAddress with non-hex chars returns false`() {
        assertFalse(handler.isMacAddress("GGHHIIJJKKLL"))
    }

    @Test
    fun `isMacAddress with colons returns false`() {
        assertFalse(handler.isMacAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `isMacAddress with dashes returns false`() {
        assertFalse(handler.isMacAddress("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `isMacAddress with IP address returns false`() {
        assertFalse(handler.isMacAddress("192.168.1.50"))
    }

    @Test
    fun `isMacAddress with empty string returns false`() {
        assertFalse(handler.isMacAddress(""))
    }

    @Test
    fun `isMacAddress with hostname returns false`() {
        assertFalse(handler.isMacAddress("wled.local"))
    }
}
