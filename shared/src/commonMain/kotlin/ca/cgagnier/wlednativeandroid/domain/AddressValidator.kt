package ca.cgagnier.wlednativeandroid.domain

import ca.cgagnier.wlednativeandroid.util.sanitizeAddress
import io.ktor.http.URLBuilder
import io.ktor.http.takeFrom

private const val SCHEME_DELIMITER = "://"
private val DISALLOWED_ADDRESS_CHARS = setOf('?', '#', '@')

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
        if (!isStructurallyValidAddress(trimmed, sanitized)) {
            return AddressValidationResult.Invalid(AddressValidationError.INVALID)
        }
        return AddressValidationResult.Valid(sanitized)
    }

    private fun isStructurallyValidAddress(trimmed: String, sanitized: String): Boolean {
        if (trimmed.any { it.isWhitespace() } || !hasValidSchemeIfPresent(trimmed)) {
            return false
        }
        if (sanitized.isEmpty() ||
            sanitized.startsWith('/') ||
            sanitized.any { it in DISALLOWED_ADDRESS_CHARS }
        ) {
            return false
        }
        return canParseHttpHost(sanitized)
    }

    private fun hasValidSchemeIfPresent(trimmed: String): Boolean {
        if (!trimmed.contains(SCHEME_DELIMITER)) return true
        val scheme = trimmed.substringBefore(SCHEME_DELIMITER).lowercase()
        val remainder = trimmed.substringAfter(SCHEME_DELIMITER)
        return (scheme == "http" || scheme == "https") && !remainder.contains(SCHEME_DELIMITER)
    }

    // URLBuilder.takeFrom throws URLParserException or IllegalArgumentException on malformed host/port
    @Suppress("TooGenericExceptionCaught")
    private fun canParseHttpHost(sanitized: String): Boolean = try {
        val url = URLBuilder().takeFrom("http://$sanitized").build()
        url.host.isNotBlank() && !url.host.contains('/')
    } catch (_: Exception) {
        false
    }
}
