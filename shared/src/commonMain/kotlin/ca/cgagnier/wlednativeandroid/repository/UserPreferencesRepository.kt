package ca.cgagnier.wlednativeandroid.repository

import androidx.datastore.core.DataStore
import ca.cgagnier.wlednativeandroid.shared.currentTimeMillis
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.map

private const val TAG: String = "UserPreferencesRepo"
private val logger = Logger.withTag(TAG)

class UserPreferencesRepository(private val dataStore: DataStore<UserPreferences>) {

    val themeMode get() = dataStore.data.map { it.theme }
    val autoDiscovery get() = dataStore.data.map { it.automaticDiscovery }
    val showOfflineDevicesLast get() = dataStore.data.map { it.showOfflineLast }
    val showHiddenDevices get() = dataStore.data.map { it.showHiddenDevices }
    val lastUpdateCheckDate get() = dataStore.data.map { it.lastUpdateCheckDate }
    val lastChangelogVersionSeen get() = dataStore.data.map { it.lastChangelogVersionSeen }

    suspend fun updateThemeMode(themeSettings: ThemeSettings) {
        logger.d { "updateThemeMode" }
        dataStore.updateData {
            it.copy(
                theme = themeSettings,
                dateLastWritten = currentTimeMillis(),
            )
        }
    }

    suspend fun updateAutoDiscovery(autoDiscover: Boolean) {
        logger.d { "updateAutoDiscovery" }
        dataStore.updateData {
            it.copy(
                automaticDiscovery = autoDiscover,
                dateLastWritten = currentTimeMillis(),
            )
        }
    }

    suspend fun updateShowOfflineDeviceLast(showOfflineDeviceLast: Boolean) {
        logger.d { "updateShowOfflineDeviceLast" }
        dataStore.updateData {
            it.copy(
                showOfflineLast = showOfflineDeviceLast,
                dateLastWritten = currentTimeMillis(),
            )
        }
    }

    suspend fun updateShowHiddenDevices(showHiddenDevices: Boolean) {
        logger.d { "updateShowHiddenDevices" }
        dataStore.updateData {
            it.copy(
                showHiddenDevices = showHiddenDevices,
                dateLastWritten = currentTimeMillis(),
            )
        }
    }

    suspend fun updateLastUpdateCheckDate(lastUpdateCheckDate: Long) {
        logger.d { "updateLastUpdateCheckDate" }
        dataStore.updateData {
            it.copy(
                lastUpdateCheckDate = lastUpdateCheckDate,
                dateLastWritten = currentTimeMillis(),
            )
        }
    }

    suspend fun updateLastChangelogVersionSeen(version: String) {
        logger.d { "updateLastChangelogVersionSeen" }
        dataStore.updateData {
            it.copy(
                lastChangelogVersionSeen = version,
                dateLastWritten = currentTimeMillis(),
            )
        }
    }
}
