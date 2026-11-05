package ca.cgagnier.wlednativeandroid.test

import kotlinx.serialization.json.Json

/**
 * Shared Json configurations for test serialization, deserialization, and snapshot comparisons.
 */
object TestJson {
    /**
     * Standard pretty-printed Json for WLED and GitHub API payloads, matching
     * network decoding configuration (lenient, unknown keys ignored, coerced input, explicit nulls false).
     */
    val api: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Pretty-printed Json for UserPreferences and migration snapshots, ensuring
     * all default values are explicitly encoded and formatted consistently.
     */
    val preferences: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
    }
}
