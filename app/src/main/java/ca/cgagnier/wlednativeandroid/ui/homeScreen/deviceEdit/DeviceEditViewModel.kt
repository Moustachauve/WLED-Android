package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

const val TAG = "DeviceEditViewModel"

@HiltViewModel
class DeviceEditViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi
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

    fun showUpdateDetails(version: String) = viewModelScope.launch(Dispatchers.IO) {
        _updateDetailsVersion.value = versionWithAssetsRepository.getVersionByTag(version)
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
                releaseService.refreshVersions(githubApi)
            } finally {
                _isCheckingUpdates.value = false
            }
        }
}
