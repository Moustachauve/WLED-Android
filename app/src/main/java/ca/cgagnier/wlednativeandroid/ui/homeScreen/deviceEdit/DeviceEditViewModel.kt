package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.RepositoryDao
import ca.cgagnier.wlednativeandroid.repository.VersionWithAssetsRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.widget.WledWidgetManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "DeviceEditViewModel"

/**
 * ViewModel for the device edit screen.
 *
 * Manages update-related dialog state, device property mutations,
 * and update-check operations. Exposes a single [uiState] flow
 * consumed by the UI composable.
 */
@Suppress("LongParameterList") // DI constructor requires multiple dependencies.
class DeviceEditViewModel(
    private val deviceRepository: DeviceRepository,
    private val repositoryDao: RepositoryDao,
    private val versionWithAssetsRepository: VersionWithAssetsRepository,
    private val githubApi: GithubApi,
    private val releaseService: ReleaseService,
    private val deviceConnectionManager: DeviceConnectionManager,
    private val widgetManager: WledWidgetManager,
    private val applicationContext: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val updateDetailsVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val updateDisclaimerVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val updateInstallVersion = MutableStateFlow<VersionWithAssets?>(null)
    private val isCheckingUpdates = MutableStateFlow(false)
    private val repository = MutableStateFlow<Repository?>(null)

    /**
     * Single combined UI state for the device edit screen.
     *
     * The [DeviceEditUiState.updateTag] field is always `null` here because
     * the update tag comes from [DeviceWithState.updateVersionTag], which
     * is owned by the caller and merged in the composable layer.
     */
    val uiState: StateFlow<DeviceEditUiState> = combine(
        isCheckingUpdates,
        repository,
        updateDetailsVersion,
        updateDisclaimerVersion,
        updateInstallVersion,
    ) { isChecking, repo, details, disclaimer, install ->
        DeviceEditUiState(
            isCheckingUpdates = isChecking,
            repository = repo,
            updateDetailsVersion = details,
            updateDisclaimerVersion = disclaimer,
            updateInstallVersion = install,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = DeviceEditUiState(),
    )

    /** Loads the [Repository] for the given [repositoryId] from the database. */
    fun loadRepository(repositoryId: Long) = viewModelScope.launch(ioDispatcher) {
        repository.value = repositoryDao.getRepositoryById(repositoryId)
    }

    /** Persists a new custom name for the [device] and updates any active widgets. */
    fun updateCustomName(device: Device, name: String) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "updateCustomName: $name")
        deviceRepository.updateCustomName(device.macAddress, name)
        widgetManager.updateWidgetDeviceDetails(applicationContext, device.copy(customName = name))
    }

    /** Toggles the hidden-from-list flag on [device]. */
    fun updateDeviceHidden(device: Device, isHidden: Boolean) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "updateDeviceHidden: ${device.originalName}, isHidden: $isHidden")
        deviceRepository.updateIsHidden(device.macAddress, isHidden)
    }

    /** Changes the update [branch] (stable / beta) for the [device]. */
    fun updateDeviceBranch(device: Device, branch: Branch) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "updateDeviceBranch: ${device.originalName}, updateChannel: $branch")
        deviceRepository.updateBranch(device.macAddress, branch)
    }

    /** Fetches and shows the release details for a specific [version] tag. */
    fun showUpdateDetails(repositoryId: Long, version: String) = viewModelScope.launch(ioDispatcher) {
        updateDetailsVersion.value = versionWithAssetsRepository.getVersionByTag(repositoryId, version)
    }

    /** Dismisses the update-details dialog. */
    fun hideUpdateDetails() {
        updateDetailsVersion.value = null
    }

    /** Marks the given [version] as skipped for [device] and closes the dialog. */
    fun skipUpdate(device: Device, version: VersionWithAssets) = viewModelScope.launch(ioDispatcher) {
        Log.d(TAG, "Saving skipUpdateTag")
        deviceRepository.updateSkipUpdateTag(device.macAddress, version.version.tagName)
        updateDetailsVersion.value = null
    }

    /** Shows the update disclaimer dialog for the given [version]. */
    fun showUpdateDisclaimer(version: VersionWithAssets) {
        updateDisclaimerVersion.value = version
    }

    /** Dismisses the update disclaimer dialog. */
    fun hideUpdateDisclaimer() {
        updateDisclaimerVersion.value = null
    }

    /** Begins the OTA install flow for [version]. */
    fun startUpdateInstall(version: VersionWithAssets) {
        updateInstallVersion.value = version
    }

    /**
     * Called when the OTA install dialog is dismissed or completed.
     * Updates device state if successful.
     */
    fun stopUpdateInstall(
        device: DeviceWithState? = null,
        version: VersionWithAssets? = updateInstallVersion.value,
        wasSuccessful: Boolean = false,
    ) {
        updateInstallVersion.value = null
        if (!wasSuccessful || device == null || version == null) {
            return
        }
        deviceConnectionManager.markUpdateInstalled(
            macAddress = device.device.macAddress,
            installedVersionTag = version.version.tagName,
        )
    }

    /**
     * Clears any skipped-update tag on [device] and refreshes available
     * versions from GitHub.
     */
    fun checkForUpdates(device: Device) {
        viewModelScope.launch(ioDispatcher) {
            isCheckingUpdates.value = true
            deviceRepository.updateSkipUpdateTag(device.macAddress, "")
            try {
                releaseService.refreshVersionsForDevice(githubApi, device)
                deviceConnectionManager.recheckUpdates()
            } finally {
                isCheckingUpdates.value = false
            }
        }
    }
}
