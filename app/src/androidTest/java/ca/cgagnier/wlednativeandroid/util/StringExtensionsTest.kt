package ca.cgagnier.wlednativeandroid.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StringExtensionsTest {

    @Test
    fun isIpAddress_validIPv4_returnsTrue() {
        assertThat("192.168.1.1".isIpAddress()).isTrue()
        assertThat("0.0.0.0".isIpAddress()).isTrue()
        assertThat("255.255.255.255".isIpAddress()).isTrue()
    }

    @Test
    fun isIpAddress_validIPv6_returnsTrue() {
        assertThat("2001:0db8:85a3:0000:0000:8a2e:0370:7334".isIpAddress()).isTrue()
        assertThat("::1".isIpAddress()).isTrue()
        assertThat("2001:db8::8a2e:370:7334".isIpAddress()).isTrue()
    }

    @Test
    fun isIpAddress_invalidIp_returnsFalse() {
        assertThat("256.0.0.0".isIpAddress()).isFalse()
        assertThat("192.168.1".isIpAddress()).isFalse()
        assertThat("not an ip".isIpAddress()).isFalse()
    }

    @Test
    fun isIpAddress_hostname_returnsFalse() {
        assertThat("www.google.com".isIpAddress()).isFalse()
        assertThat("localhost".isIpAddress()).isFalse()
    }

    @Test
    fun isIpAddress_emptyString_returnsFalse() {
        assertThat("".isIpAddress()).isFalse()
    }
}