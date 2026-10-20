package ca.cgagnier.wlednativeandroid.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressValidatorTest {

    private val validator = AddressValidator()

    @Test
    fun validateReturnsEmptyErrorWhenBlank() {
        listOf("", "   ", "\t\n").forEach { blank ->
            assertEquals(
                AddressValidationResult.Invalid(AddressValidationError.EMPTY),
                validator.validate(blank),
            )
        }
    }

    @Test
    fun validateReturnsValidWithSanitizedAddressForValidInputs() {
        mapOf(
            "192.168.1.50" to "192.168.1.50",
            "  http://192.168.1.50/  " to "192.168.1.50",
            "https://wled.local//" to "wled.local",
            "wled.local:8080" to "wled.local:8080",
            "http://my.domain.com/wled/" to "my.domain.com/wled",
            "[2001:db8::1]" to "[2001:db8::1]",
        ).forEach { (input, expectedSanitized) ->
            assertEquals(
                AddressValidationResult.Valid(expectedSanitized),
                validator.validate(input),
                "Expected '$input' to validate to '$expectedSanitized'",
            )
        }
    }

    @Test
    fun validateReturnsInvalidErrorForMalformedAddresses() {
        listOf(
            "192.168.1.50/my wled",
            "wled .local",
            "http://",
            "https:///",
            "ftp://192.168.1.50",
            "http://http://192.168.1.50",
            "/just-a-path",
            "192.168.1.50?foo=bar",
            "192.168.1.50#section",
            "user@192.168.1.50",
            "192.168.1.50:notaport",
        ).forEach { invalid ->
            assertEquals(
                AddressValidationResult.Invalid(AddressValidationError.INVALID),
                validator.validate(invalid),
                "Expected '$invalid' to be rejected as INVALID",
            )
        }
    }
}
