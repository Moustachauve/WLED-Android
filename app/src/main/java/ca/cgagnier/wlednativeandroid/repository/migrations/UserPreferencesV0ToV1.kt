package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.datastore.core.DataMigration
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferences

class UserPreferencesV0ToV1 : DataMigration<UserPreferences> {
    override suspend fun cleanUp() {
    }

    override suspend fun migrate(currentData: UserPreferences): UserPreferences {
        return currentData.toBuilder()
            .setThemeValue(ThemeSettings.Auto_VALUE)
            .setAutomaticDiscovery(true)
            .setShowOfflineLast(true)
            .setSendCrashData(false)
            .setSendPerformanceData(false)
            .setVersion(1)
            .build()
    }

    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean {
        return currentData.version <= 0
    }
}
