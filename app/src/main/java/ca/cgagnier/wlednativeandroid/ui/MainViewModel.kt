package ca.cgagnier.wlednativeandroid.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DEFAULT_REPO
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.update.getRepositoryFromInfo
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClientManager
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
    private val githubApi: GithubApi,
    private val deviceRepository: DeviceRepository,
    private val websocketClientManager: WebsocketClientManager
) : ViewModel() {

    fun downloadUpdateMetadata() {
        viewModelScope.launch(Dispatchers.IO) {
            val lastCheckDate = userPreferencesRepository.lastUpdateCheckDate.first()
            val now = System.currentTimeMillis()
            if (now < lastCheckDate) {
                Log.i(TAG, "Not updating version list since it was done recently.")
                return@launch
            }
            
            // Collect unique repositories from all connected devices
            val repositories = mutableSetOf<String>()
            repositories.add(DEFAULT_REPO) // Always include the default WLED repository
            
            websocketClientManager.getClients().values.forEach { client ->
                val info = client.deviceState.stateInfo.value?.info
                if (info != null) {
                    val repo = getRepositoryFromInfo(info)
                    repositories.add(repo)
                    Log.d(TAG, "Found device using repository: $repo")
                }
            }
            
            Log.i(TAG, "Refreshing versions from ${repositories.size} repositories: $repositories")
            releaseService.refreshVersions(githubApi, repositories)
            // Set the next date to check in minimum 24 hours from now.
            userPreferencesRepository.updateLastUpdateCheckDate(now + DAYS.toMillis(1))
        }
    }
}