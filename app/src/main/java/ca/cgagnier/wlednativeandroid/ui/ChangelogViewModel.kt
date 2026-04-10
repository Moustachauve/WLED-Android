package ca.cgagnier.wlednativeandroid.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.BuildConfig
import ca.cgagnier.wlednativeandroid.domain.ChangelogProvider
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val changelogProvider: ChangelogProvider,
) : ViewModel() {

    private val _changelogContent = MutableStateFlow<String?>(null)
    val changelogContent: StateFlow<String?> = _changelogContent.asStateFlow()

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

        val content = changelogProvider.getChangelog(lastSeenVersion, currentVersion)

        if (!content.isNullOrEmpty()) {
            _changelogContent.value = content
        } else {
            // No changelogs to show, but version has changed, save logic so we don`t check again.
            userPreferencesRepository.updateLastChangelogVersionSeen(currentVersion)
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            userPreferencesRepository.updateLastChangelogVersionSeen(BuildConfig.VERSION_NAME)
            _changelogContent.value = null
        }
    }

    fun showAllChangelogs() {
        viewModelScope.launch {
            val content = changelogProvider.getChangelog("0.0.0", BuildConfig.VERSION_NAME)
            if (!content.isNullOrEmpty()) {
                _changelogContent.value = content
            }
        }
    }
}
