package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.DEFAULT_REPO
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TAG = "DeviceEditViewModel"

@HiltViewModel
@Suppress("LongParameterList") // DI constructor requires multiple dependencies
class DeviceEditViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi,
    private val releaseService: ReleaseService,
    private val widgetManager: WledWidgetManager,
    @param:ApplicationContext private val applicationContext: Context,
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
        val updatedDevice = device.copy(
            customName = name,
        )

        Log.d(TAG, "updateCustomName: $name")

        deviceRepository.update(updatedDevice)

        // Update widgets to show the new name
        widgetManager.updateWidgetDeviceDetails(applicationContext, updatedDevice)
    }

    fun updateDeviceHidden(device: Device, isHidden: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "updateDeviceHidden: ${device.originalName}, isHidden: $isHidden")
        deviceRepository.update(
            device.copy(
                isHidden = isHidden,
            ),
        )
    }

    fun updateDeviceBranch(device: Device, branch: Branch) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "updateDeviceBranch: ${device.originalName}, updateChannel: $branch")
        val updatedDevice = device.copy(
            branch = branch,
        )
        deviceRepository.update(updatedDevice)
    }

    fun showUpdateDetails(repositoryId: Long, version: String) = viewModelScope.launch(Dispatchers.IO) {
        _updateDetailsVersion.value = versionWithAssetsRepository.getVersionByTag(repositoryId, version)
    }

    fun hideUpdateDetails() {
        _updateDetailsVersion.value = null
    }

    fun skipUpdate(device: Device, version: VersionWithAssets) = viewModelScope.launch(Dispatchers.IO) {
        Log.d(TAG, "Saving skipUpdateTag")
        val updatedDevice = device.copy(
            skipUpdateTag = version.version.tagName,
        )
        deviceRepository.update(updatedDevice)
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

    fun checkForUpdates(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCheckingUpdates.value = true
            val updatedDevice = device.copy(skipUpdateTag = "")
            deviceRepository.update(updatedDevice)
            try {
                val repo = repositoryDao.getRepositoryById(device.repositoryId)
                val repoStr = repo?.ownerAndRepo ?: DEFAULT_REPO
                releaseService.refreshVersions(githubApi, setOf(repoStr))
            } finally {
                _isCheckingUpdates.value = false
            }
        }
    }
}
