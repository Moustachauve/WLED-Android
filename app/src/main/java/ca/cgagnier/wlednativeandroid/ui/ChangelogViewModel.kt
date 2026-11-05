package ca.cgagnier.wlednativeandroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.BuildConfig
import ca.cgagnier.wlednativeandroid.domain.ChangelogProvider
import ca.cgagnier.wlednativeandroid.domain.VersionChangelog
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ChangelogViewModel(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val changelogProvider: ChangelogProvider,
) : ViewModel() {

    private val _changelogs = MutableStateFlow<List<VersionChangelog>>(emptyList())
    val changelogs: StateFlow<List<VersionChangelog>> = _changelogs.asStateFlow()

    init {
        viewModelScope.launch {
            checkChangelog()
        }
    }

    private suspend fun checkChangelog() {
        val lastSeenVersion = userPreferencesRepository.lastChangelogVersionSeen.first()
        val currentVersion = BuildConfig.VERSION_NAME

        if (lastSeenVersion.isEmpty()) {
            // First install, don't show changelog, just save the version
            userPreferencesRepository.updateLastChangelogVersionSeen(currentVersion)
            return
        }

        if (lastSeenVersion == currentVersion) {
            // Already saw the latest
            return
        }

        val entries = changelogProvider.getChangelog(lastSeenVersion, currentVersion)

        if (entries.isNotEmpty()) {
            _changelogs.value = entries
        } else {
            // No changelogs to show, but version has changed, save logic so we don't check again.
            userPreferencesRepository.updateLastChangelogVersionSeen(currentVersion)
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            userPreferencesRepository.updateLastChangelogVersionSeen(BuildConfig.VERSION_NAME)
            _changelogs.value = emptyList()
        }
    }

    fun showAllChangelogs() {
        viewModelScope.launch {
            val entries = changelogProvider.getChangelog("0.0.0", BuildConfig.VERSION_NAME)
            if (entries.isNotEmpty()) {
                _changelogs.value = entries
            }
        }
    }
}
