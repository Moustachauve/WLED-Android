package ca.cgagnier.wlednativeandroid.service.soundsync.dsp

import kotlinx.serialization.Serializable

/**
 * Automatic Gain Control (AGC) operating modes modeled after WLED's Sound Settings.
 *
 * Each active mode defines two-zone tracking parameters:
 * - [targetLevel]: Target peak amplitude (`0.0..1.0`) that AGC aims to maintain.
 * - [peakDecayTimeSec]: Exponential decay time constant (in seconds) for the tracked input peak.
 * - [slowFollowTimeSec]: Gain adjustment time constant (in seconds) when the amplified signal
 *   sits inside the normal operating zone (`[zoneLow]..[zoneHigh]`), preserving musical dynamics.
 * - [fastFollowTimeSec]: Gain adjustment time constant (in seconds) when the amplified signal
 *   enters the emergency zone (below [zoneLow] or above [zoneHigh]), reacting quickly to
 *   prevent sustained clipping or dead LEDs.
 */
@Serializable
enum class AgcMode(
    val targetLevel: Float,
    val peakDecayTimeSec: Float,
    val slowFollowTimeSec: Float,
    val fastFollowTimeSec: Float,
    val zoneLow: Float,
    val zoneHigh: Float,
) {
    /** No automatic adjustment; WLED uses the manual Gain value as set. */
    OFF(
        targetLevel = 0f,
        peakDecayTimeSec = 0f,
        slowFollowTimeSec = 0f,
        fastFollowTimeSec = 0f,
        zoneLow = 0f,
        zoneHigh = 0f,
    ),

    /** Smoothly follows changes in volume. A good default for most setups. */
    NORMAL(
        targetLevel = 0.60f,
        peakDecayTimeSec = 4.0f,
        slowFollowTimeSec = 3.0f,
        fastFollowTimeSec = 0.20f,
        zoneLow = 0.12f,
        zoneHigh = 0.90f,
    ),

    /** Reacts quickly to volume changes for a more dramatic LED response to dynamics. */
    VIVID(
        targetLevel = 0.65f,
        peakDecayTimeSec = 1.5f,
        slowFollowTimeSec = 1.2f,
        fastFollowTimeSec = 0.12f,
        zoneLow = 0.10f,
        zoneHigh = 0.90f,
    ),

    /** Slower to adjust; works well for GEQ effects or music with wide dynamic range. */
    LAZY(
        targetLevel = 0.55f,
        peakDecayTimeSec = 8.0f,
        slowFollowTimeSec = 6.0f,
        fastFollowTimeSec = 0.35f,
        zoneLow = 0.14f,
        zoneHigh = 0.94f,
    ),
}
