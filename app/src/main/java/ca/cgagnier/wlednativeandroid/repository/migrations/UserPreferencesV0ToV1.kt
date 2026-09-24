package ca.cgagnier.wlednativeandroid.repository.migrations

import androidx.datastore.core.DataMigration
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.repository.UserPreferences

class UserPreferencesV0ToV1 : DataMigration<UserPreferences> {
    override suspend fun cleanUp() {
    }

    override suspend fun migrate(currentData: UserPreferences): UserPreferences = currentData.copy(
        theme = ThemeSettings.Auto,
        automaticDiscovery = true,
        showOfflineLast = true,
        sendCrashData = false,
        sendPerformanceData = false,
        version = 1,
    )

    override suspend fun shouldMigrate(currentData: UserPreferences): Boolean = currentData.version <= 0
}
