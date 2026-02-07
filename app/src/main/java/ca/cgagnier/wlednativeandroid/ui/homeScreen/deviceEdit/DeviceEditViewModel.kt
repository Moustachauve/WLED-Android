package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.update.getRepositoryFromInfo
import ca.cgagnier.wlednativeandroid.service.websocket.WebsocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TAG = "DeviceEditViewModel"
private const val DEFAULT_REPO = "wled/WLED"

@HiltViewModel
class DeviceEditViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi,
    private val websocketClients: Map<String, @JvmSuppressWildcards WebsocketClient>
) : ViewModel() {

    private var _updateDetailsVersion: MutableStateFlow<VersionWithAssets?> = MutableStateFlow(null)
    val updateDetailsVersion = _updateDetailsVersion.asStateFlow()

    private var _updateDisclaimerVersion: MutableStateFlow<VersionWithAssets?> =
        MutableStateFlow(null)
    val updateDisclaimerVersion = _updateDisclaimerVersion.asStateFlow()

    private var _updateInstallVersion: MutableStateFlow<VersionWithAssets?> = MutableStateFlow(null)
    val updateInstallVersion = _updateInstallVersion.asStateFlow()

    private var _isCheckingUpdates = MutableStateFlow(false)
    val isCheckingUpdates = _isCheckingUpdates.asStateFlow()

    fun updateCustomName(device: Device, name: String) = viewModelScope.launch(Dispatchers.IO) {
        val isCustomName = name != ""
        val updatedDevice = device.copy(
            customName = name,
        )

        Log.d(TAG, "updateCustomName: $name, isCustom: $isCustomName")

        repository.update(updatedDevice)
    }

    fun updateDeviceHidden(device: Device, isHidden: Boolean) =
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "updateDeviceHidden: ${device.originalName}, isHidden: $isHidden")
            repository.update(
                device.copy(
                    isHidden = isHidden
                )
            )
        }

    fun updateDeviceBranch(device: Device, branch: Branch) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "updateDeviceBranch: ${device.originalName}, updateChannel: $branch")
        val updatedDevice = device.copy(
            branch = branch
        )
        repository.update(updatedDevice)
    }

    fun showUpdateDetails(device: Device, deviceStateInfo: DeviceStateInfo?, version: String) = viewModelScope.launch(Dispatchers.IO) {
        // Extract repository from device info, defaulting to "wled/WLED"
        val repository = deviceStateInfo?.info?.repo ?: "wled/WLED"
        _updateDetailsVersion.value = versionWithAssetsRepository.getVersionByTag(repository, version)
    }

    fun hideUpdateDetails() {
        _updateDetailsVersion.value = null
    }

    fun skipUpdate(device: Device, version: VersionWithAssets) =
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Saving skipUpdateTag")
            val updatedDevice = device.copy(
                skipUpdateTag = version.version.tagName
            )
            repository.update(updatedDevice)
            _updateDetailsVersion.value = null
        }

    fun showUpdateDisclaimer(version: VersionWithAssets) {
        _updateDisclaimerVersion.value = version
    }

    fun hideUpdateDisclaimer() {
        _updateDisclaimerVersion.value = null
    }

    fun startUpdateInstall(version: VersionWithAssets) {
        _updateInstallVersion.value = version
    }

    fun stopUpdateInstall() {
        _updateInstallVersion.value = null
    }

    fun checkForUpdates(device: Device) =
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdates.value = true
            val updatedDevice = device.copy(skipUpdateTag = "")
            repository.update(updatedDevice)
            try {
                val releaseService = ReleaseService(versionWithAssetsRepository)
                
                // Get repository for this specific device
                val repositories = mutableSetOf<String>()
                repositories.add(DEFAULT_REPO) // Always include the default WLED repository
                
                // Look up the specific device's websocket client to get its repository
                val client = websocketClients[device.macAddress]
                val info = client?.deviceState?.stateInfo?.value?.info
                if (info != null) {
                    val repo = getRepositoryFromInfo(info)
                    repositories.add(repo)
                    Log.d(TAG, "Refreshing versions for device repository: $repo")
                } else {
                    Log.d(TAG, "Device info not available, using default repository only")
                }
                
                Log.i(TAG, "Refreshing versions from ${repositories.size} repositories: $repositories")
                releaseService.refreshVersions(githubApi, repositories)
            } finally {
                _isCheckingUpdates.value = false
            }
        }
}
