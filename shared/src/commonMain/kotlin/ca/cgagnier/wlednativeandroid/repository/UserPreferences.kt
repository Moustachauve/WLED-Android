package ca.cgagnier.wlednativeandroid.repository

import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.AgcMode
import ca.cgagnier.wlednativeandroid.service.soundsync.dsp.SoundSyncProcessor
import kotlinx.serialization.Serializable

@Serializable
data class UserPreferences(
    val selectedDeviceAddress: String = "",
    val hasMigratedSharedPref: Boolean = false,
    val theme: ThemeSettings = ThemeSettings.Auto,
    val automaticDiscovery: Boolean = true,
    val version: Int = 1,
    val showOfflineLast: Boolean = true,
    val sendCrashData: Boolean = false,
    val sendPerformanceData: Boolean = false,
    val lastUpdateCheckDate: Long = 0L,
    val dateLastWritten: Long = 0L,
    val showHiddenDevices: Boolean = false,
    val lastChangelogVersionSeen: String = "",
    val soundSyncGain: Float = 1.0f,
    val soundSyncAgcEnabled: Boolean = false,
    val soundSyncAgcMode: AgcMode = AgcMode.NORMAL,
    val soundSyncSquelch: Int = SoundSyncProcessor.DEFAULT_SQUELCH,
    val soundSyncBetaEnabled: Boolean = false,
)
