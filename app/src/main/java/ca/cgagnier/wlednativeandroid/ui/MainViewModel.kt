package ca.cgagnier.wlednativeandroid.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject

private const val TAG = "MainViewModel"

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val releaseService: ReleaseService,
    private val githubApi: GithubApi
) : ViewModel() {

    fun downloadUpdateMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastCheckDate = userPreferencesRepository.lastUpdateCheckDate.first()
            val now = System.currentTimeMillis()
            if (now < lastCheckDate) {
                Log.i(TAG, "Not updating version list since it was done recently.")
                return@launch
            }
            releaseService.refreshVersions(githubApi)
            // Set the next date to check in minimum 24 hours from now.
            userPreferencesRepository.updateLastUpdateCheckDate(now + DAYS.toMillis(1))
        }
    }
}