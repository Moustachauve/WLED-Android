package ca.cgagnier.wlednativeandroid.util

private const val IPV4_PART_COUNT = 4
private const val IPV4_MAX_PART_LENGTH = 3
private const val IPV4_MAX_OCTET = 255
private const val IPV6_MAX_GROUPS = 8
private const val IPV6_MAX_GROUP_LENGTH = 4
private const val HEX_RADIX = 16

/**
 * Strips any leading scheme (e.g. `http://`, `https://`), trims surrounding whitespace,
 * and removes trailing slashes so that a clean host/path string is stored.
 */
fun String.sanitizeAddress(): String = trim().substringAfter("://").trimEnd('/')

/**
 * Checks whether this string is a numeric IPv4 or IPv6 literal (with optional bracketed IPv6
 * notation) without performing any DNS lookups.
 */
fun String.isIpAddress(): Boolean {
    val candidate = trim().removeSurrounding("[", "]")
    return isIpv4Literal(candidate) || isIpv6Literal(candidate)
}

private fun isIpv4Literal(input: String): Boolean {
    val parts = input.split('.')
    if (parts.size != IPV4_PART_COUNT) return false
    return parts.all { part ->
        part.length in 1..IPV4_MAX_PART_LENGTH &&
            part.all { it.isDigit() } &&
            (part.length == 1 || !part.startsWith('0')) &&
            (part.toIntOrNull() ?: -1) in 0..IPV4_MAX_OCTET
    }
}

private fun isIpv6Literal(input: String): Boolean {
    if (!input.contains(':')) return false
    val doubleColonParts = input.split("::")
    if (doubleColonParts.size > 2) return false

    val hasCompression = doubleColonParts.size == 2
    val leftGroups = parseIpv6Side(doubleColonParts[0])
    val rightGroups = if (hasCompression) parseIpv6Side(doubleColonParts[1]) else emptyList()
    if (leftGroups == null || rightGroups == null) return false

    val totalGroups = leftGroups.size + rightGroups.size
    val groupCountValid = if (hasCompression) {
        totalGroups < IPV6_MAX_GROUPS
    } else {
        totalGroups == IPV6_MAX_GROUPS
    }
    return groupCountValid && (leftGroups + rightGroups).all(::isValidIpv6Group)
}

private fun parseIpv6Side(side: String): List<String>? {
    if (side.isEmpty()) return emptyList()
    val groups = side.split(':')
    return if (groups.any { it.isEmpty() }) null else groups
}

private fun isValidIpv6Group(group: String): Boolean = group.length in 1..IPV6_MAX_GROUP_LENGTH &&
    group.all { it.digitToIntOrNull(HEX_RADIX) != null }
