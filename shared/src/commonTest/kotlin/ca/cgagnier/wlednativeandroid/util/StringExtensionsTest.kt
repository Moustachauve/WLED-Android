package ca.cgagnier.wlednativeandroid.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StringExtensionsTest {

    @Test
    fun isIpAddressReturnsTrueForValidIpv4Addresses() {
        listOf(
            "192.168.1.1",
            "0.0.0.0",
            "255.255.255.255",
            "10.0.0.50",
        ).forEach { ip ->
            assertTrue(ip.isIpAddress(), "Expected '$ip' to be recognized as an IPv4 address")
        }
    }

    @Test
    fun isIpAddressReturnsTrueForValidIpv6Addresses() {
        listOf(
            "2001:0db8:85a3:0000:0000:8a2e:0370:7334",
            "::1",
            "2001:db8::8a2e:370:7334",
            "[2001:db8::1]",
        ).forEach { ip ->
            assertTrue(ip.isIpAddress(), "Expected '$ip' to be recognized as an IPv6 address")
        }
    }

    @Test
    fun isIpAddressReturnsFalseForInvalidIpLiterals() {
        listOf(
            "256.0.0.0",
            "192.168.1",
            "192.168.1.1.1",
            "192.168.01.1",
            "-1.0.0.1",
            "not an ip",
            "2001:db8::1::2",
            "2001:db8:zzzz::1",
        ).forEach { invalid ->
            assertFalse(invalid.isIpAddress(), "Expected '$invalid' not to be recognized as an IP")
        }
    }

    @Test
    fun isIpAddressReturnsFalseForHostnamesAndEmptyStrings() {
        listOf(
            "",
            "   ",
            "wled.local",
            "www.google.com",
            "localhost",
        ).forEach { hostname ->
            assertFalse(hostname.isIpAddress(), "Expected '$hostname' not to be recognized as an IP")
        }
    }

    @Test
    fun sanitizeAddressStripsSchemeWhitespaceAndTrailingSlashes() {
        mapOf(
            "http://192.168.1.50/" to "192.168.1.50",
            "https://wled.local///" to "wled.local",
            "  HTTP://my.domain.com/wled/  " to "my.domain.com/wled",
            "192.168.1.50" to "192.168.1.50",
        ).forEach { (raw, expected) ->
            assertEquals(expected, raw.sanitizeAddress())
        }
    }
}
