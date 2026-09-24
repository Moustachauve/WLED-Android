package ca.cgagnier.wlednativeandroid.repository.migrations

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataMigration
import androidx.datastore.dataStoreFile
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import ca.cgagnier.wlednativeandroid.repository.legacy.ThemeSettings as LegacyThemeSettings
import ca.cgagnier.wlednativeandroid.repository.legacy.UserPreferences as LegacyProtoUserPreferences

class LegacyProtoToKotlinxPreferencesMigration(private val legacyFile: File) : DataMigration<UserPreferences> {

    constructor(context: Context) : this(context.dataStoreFile(LEGACY_DATA_STORE_FILE_NAME))

    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean = withContext(Dispatchers.IO) {
        legacyFile.exists() && legacyFile.length() > 0
    }

    override suspend fun migrate(currentData: UserPreferences): UserPreferences = withContext(Dispatchers.IO) {
        try {
            if (!legacyFile.exists() || legacyFile.length() == 0L) {
                return@withContext currentData
            }
            legacyFile.inputStream().use { inputStream ->
                val legacyProto = LegacyProtoUserPreferences.parseFrom(inputStream)
                val theme = when (legacyProto.theme) {
                    LegacyThemeSettings.Dark -> ThemeSettings.Dark
                    LegacyThemeSettings.Light -> ThemeSettings.Light
                    else -> ThemeSettings.Auto
                }
                val isV0 = legacyProto.version <= 0
                UserPreferences(
                    selectedDeviceAddress = legacyProto.selectedDeviceAddress,
                    hasMigratedSharedPref = legacyProto.hasMigratedSharedPref,
                    theme = if (isV0) ThemeSettings.Auto else theme,
                    automaticDiscovery = if (isV0) true else legacyProto.automaticDiscovery,
                    version = maxOf(legacyProto.version, 1),
                    showOfflineLast = if (isV0) true else legacyProto.showOfflineLast,
                    sendCrashData = legacyProto.sendCrashData,
                    sendPerformanceData = legacyProto.sendPerformanceData,
                    lastUpdateCheckDate = legacyProto.lastUpdateCheckDate,
                    dateLastWritten = legacyProto.dateLastWritten,
                    showHiddenDevices = legacyProto.showHiddenDevices,
                    lastChangelogVersionSeen = legacyProto.lastChangelogVersionSeen,
                )
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Log.e(TAG, "Failed to migrate legacy proto user preferences", e)
            currentData
        }
    }

    override suspend fun cleanUp() {
        withContext(Dispatchers.IO) {
            try {
                if (legacyFile.exists()) {
                    val deleted = legacyFile.delete()
                    if (!deleted) {
                        Log.w(TAG, "Failed to delete legacy file: ${legacyFile.absolutePath}")
                    }
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                Log.e(TAG, "Error cleaning up legacy user preferences file", e)
            }
        }
    }

    companion object {
        const val LEGACY_DATA_STORE_FILE_NAME = "user_prefs.pb"
        private const val TAG = "LegacyProtoMigration"
    }
}
