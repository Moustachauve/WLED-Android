package ca.cgagnier.wlednativeandroid.domain

import android.content.Intent
import android.net.Uri
import ca.cgagnier.wlednativeandroid.model.DEFAULT_WLED_AP_IP
import javax.inject.Inject

/**
 * Represents a parsed deep link result.
 */
sealed class DeepLink {
    /** Deep link with a MAC address identifier. */
    data class MacAddress(val mac: String) : DeepLink()

    /** Deep link with a network address (IP or hostname). */
    data class Address(val address: String) : DeepLink()

    /** Deep link to AP mode (4.3.2.1). */
    data object ApMode : DeepLink()
}

/**
 * Handles parsing of deep links from intents.
 *
 * Supports:
 * - wled://{mac_address} - e.g., wled://AABBCCDDEEFF
 * - wled://{ip_or_hostname} - e.g., wled://192.168.1.50 or wled://wled.local
 * - http://4.3.2.1 - Default WLED AP mode IP
 */
class DeepLinkHandler @Inject constructor() {

    companion object {
        private const val SCHEME_WLED = "wled"
        private const val SCHEME_HTTP = "http"
        private val MAC_ADDRESS_REGEX = Regex("^[0-9A-Fa-f]{12}$")
    }

    /**
     * Parses an Intent and returns the corresponding [DeepLink] if valid.
     *
     * @param intent The intent to parse
     * @return The parsed [DeepLink] or null if not a valid deep link
     */
    fun parseIntent(intent: Intent?): DeepLink? {
        if (intent?.action != Intent.ACTION_VIEW) {
            return null
        }
        return parseUri(intent.data)
    }

    /**
     * Parses a URI and returns the corresponding [DeepLink] if valid.
     *
     * @param uri The URI to parse
     * @return The parsed [DeepLink] or null if not a valid deep link URI
     */
    fun parseUri(uri: Uri?): DeepLink? {
        uri ?: return null

        return when (uri.scheme?.lowercase()) {
            SCHEME_WLED -> parseWledScheme(uri)
            SCHEME_HTTP -> parseHttpScheme(uri)
            else -> null
        }
    }

    private fun parseWledScheme(uri: Uri): DeepLink? {
        // wled://identifier where identifier is the host
        val identifier = uri.host?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        return if (isMacAddress(identifier)) {
            DeepLink.MacAddress(identifier.uppercase())
        } else {
            DeepLink.Address(identifier)
        }
    }

    private fun parseHttpScheme(uri: Uri): DeepLink? {
        val host = uri.host ?: return null

        return if (host == DEFAULT_WLED_AP_IP) {
            DeepLink.ApMode
        } else {
            null
        }
    }

    /**
     * Checks if the given string is a valid MAC address format (12 hex characters).
     *
     * @param identifier The string to check
     * @return true if it's a valid MAC address format
     */
    fun isMacAddress(identifier: String): Boolean = MAC_ADDRESS_REGEX.matches(identifier)
}
