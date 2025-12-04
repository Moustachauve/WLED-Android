package ca.cgagnier.wlednativeandroid.domain.use_case

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateAddressTest {

    private val validateAddress = ValidateAddress()

    @Test
    fun execute_validAddress_returnsSuccess() {
        val result = validateAddress.execute("192.168.1.100")
        assertTrue(result.successful)
    }

    @Test
    fun execute_validHostname_returnsSuccess() {
        val result = validateAddress.execute("wled.local")
        assertTrue(result.successful)
    }

    @Test
    fun execute_emptyAddress_returnsError() {
        val result = validateAddress.execute("")
        assertFalse(result.successful)
    }

    @Test
    fun execute_addressWithSpaces_returnsError() {
        val result = validateAddress.execute("192. 168.1.100")
        assertFalse(result.successful)
    }
}
