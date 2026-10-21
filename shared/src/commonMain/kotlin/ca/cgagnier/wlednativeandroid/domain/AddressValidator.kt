package ca.cgagnier.wlednativeandroid.domain

import ca.cgagnier.wlednativeandroid.util.sanitizeAddress

enum class AddressValidationError {
    EMPTY,
    INVALID,
}

sealed class AddressValidationResult {
    data class Valid(val sanitizedAddress: String) : AddressValidationResult()
    data class Invalid(val error: AddressValidationError) : AddressValidationResult()
}

class AddressValidator {
    fun validate(rawAddress: String): AddressValidationResult {
        val trimmed = rawAddress.trim()
        if (trimmed.isEmpty()) {
            return AddressValidationResult.Invalid(AddressValidationError.EMPTY)
        }
        val sanitized = trimmed.sanitizeAddress()
        if (sanitized.isEmpty() || sanitized.any { it.isWhitespace() } || sanitized.startsWith('/')) {
            return AddressValidationResult.Invalid(AddressValidationError.INVALID)
        }
        return AddressValidationResult.Valid(sanitized)
    }
}
