package ca.cgagnier.wlednativeandroid.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressValidatorTest {

    private val validator = AddressValidator()

    @Test
    fun `validate returns EMPTY error for blank inputs`() {
        val emptyInputs = listOf("", "   ", "\t\n")
        emptyInputs.forEach { input ->
            assertEquals(
                AddressValidationResult.Invalid(AddressValidationError.EMPTY),
                validator.validate(input),
                "Expected EMPTY for '$input'",
            )
        }
    }

    @Test
    fun `validate returns Valid with sanitized address for valid inputs`() {
        val validCases = listOf(
            "192.168.1.50" to "192.168.1.50",
            "  192.168.1.50  " to "192.168.1.50",
            "http://192.168.1.50/" to "192.168.1.50",
            "https://wled.local///" to "wled.local",
            "wled-living-room.local" to "wled-living-room.local",
            "wled.local:8080" to "wled.local:8080",
            "my.domain.com/wled" to "my.domain.com/wled",
            "http://my.domain.com:8080/wled/" to "my.domain.com:8080/wled",
            "[fe80::1]" to "[fe80::1]",
            "http://[fe80::1]:8080/" to "[fe80::1]:8080",
        )
        validCases.forEach { (input, expected) ->
            assertEquals(
                AddressValidationResult.Valid(expected),
                validator.validate(input),
                "Expected Valid('$expected') for '$input'",
            )
        }
    }

    @Test
    fun `validate returns INVALID error for malformed inputs`() {
        val invalidInputs = listOf(
            "http://",
            "https:///",
            "/",
            "///",
            "/path/without/host",
            "http:///path",
            "192.168.1. 50",
            "wled\tlocal",
        )
        invalidInputs.forEach { input ->
            assertEquals(
                AddressValidationResult.Invalid(AddressValidationError.INVALID),
                validator.validate(input),
                "Expected INVALID for '$input'",
            )
        }
    }
}
