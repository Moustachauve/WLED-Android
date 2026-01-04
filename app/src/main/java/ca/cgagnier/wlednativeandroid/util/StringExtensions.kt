package ca.cgagnier.wlednativeandroid.util

import android.net.InetAddresses
import android.os.Build
import android.util.Patterns
import java.net.InetAddress

fun String.isIpAddress(): Boolean {
    // API 29+ (Android 10)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return InetAddresses.isNumericAddress(this)
    }
    // Fallback for older Android versions
    // Check IPv4 using the system Pattern
    if (Patterns.IP_ADDRESS.matcher(this).matches()) {
        return true
    }

    // Check IPv6
    // We only try parsing if the string contains a colon, indicating it MIGHT be IPv6.
    // This prevents triggering a DNS lookup for standard hostnames (like "google.com"),
    // which would block the thread and cause a crash or ANR.
    if (this.contains(":")) {
        return try {
            // getByName parses numeric IPs without network calls.
            // It only triggers DNS if passed a hostname (which we avoid with the ':' check).
            InetAddress.getByName(this)
            true
        } catch (e: Exception) {
            false
        }
    }

    return false
}