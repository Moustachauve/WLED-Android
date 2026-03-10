package ca.cgagnier.wlednativeandroid.ui

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.domain.DeepLink
import ca.cgagnier.wlednativeandroid.domain.DeepLinkHandler
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.api.github.GithubApi
import ca.cgagnier.wlednativeandroid.service.update.ReleaseService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject

private const val TAG = "MainViewModel"

/**
 * Represents the state of deep link processing.
 */
sealed class DeepLinkState {
    data object Idle : DeepLinkState()
    data object Loading : DeepLinkState()
    data class NavigateToDevice(val event: NavigationEvent) : DeepLinkState()
    data class Error(val messageResId: Int, val address: String) : DeepLinkState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val releaseService: ReleaseService,
    private val githubApi: GithubApi,
    private val deviceRepository: DeviceRepository,
    private val deviceFirstContactService: DeviceFirstContactService,
    private val deepLinkHandler: DeepLinkHandler,
) : ViewModel() {

    private val _deepLinkState = MutableStateFlow<DeepLinkState>(DeepLinkState.Idle)
    val deepLinkState: StateFlow<DeepLinkState> = _deepLinkState.asStateFlow()

    private var deepLinkJob: kotlinx.coroutines.Job? = null

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

    /**
     * Handles an intent that may contain a deep link or widget navigation.
     *
     * @param intent The intent to process
     */
    fun handleIntent(intent: Intent?) {
        intent ?: return

        // First check for widget-style intent extra (uses MAC address)
        val extraMacAddress = intent.getStringExtra(MainActivity.EXTRA_DEVICE_MAC_ADDRESS)
        if (extraMacAddress != null) {
            Log.d(TAG, "Handling widget intent with MAC: $extraMacAddress")
            navigateToDeviceByMac(extraMacAddress)
            return
        }

        // Then check for deep link
        val deepLink = deepLinkHandler.parseIntent(intent)
        if (deepLink != null) {
            Log.d(TAG, "Handling deep link: $deepLink")
            handleDeepLink(deepLink)
        }
    }

    /**
     * Navigates directly to a device by its MAC address.
     * Used by the widget for direct navigation to known devices.
     *
     * @param macAddress The MAC address of the device
     */
    private fun navigateToDeviceByMac(macAddress: String) {
        _deepLinkState.value = DeepLinkState.NavigateToDevice(NavigationEvent(macAddress))
    }

    private fun handleDeepLink(deepLink: DeepLink) {
        deepLinkJob?.cancel()
        deepLinkJob = viewModelScope.launch(Dispatchers.IO) {
            when (deepLink) {
                is DeepLink.MacAddress -> handleMacAddressDeepLink(deepLink.mac)
                is DeepLink.Address -> handleAddressDeepLink(deepLink.address)
                is DeepLink.ApMode -> handleApModeDeepLink()
            }
        }
    }

    private suspend fun handleMacAddressDeepLink(macAddress: String) {
        Log.d(TAG, "Looking up device by MAC: $macAddress")
        val device = deviceRepository.findDeviceByMacAddress(macAddress)

        if (device != null) {
            Log.d(TAG, "Found device: ${device.macAddress}")
            _deepLinkState.value = DeepLinkState.NavigateToDevice(NavigationEvent(device.macAddress))
        } else {
            Log.w(TAG, "Device not found for MAC: $macAddress")
            _deepLinkState.value = DeepLinkState.Error(
                messageResId = R.string.deep_link_error_mac_not_found,
                address = macAddress,
            )
        }
    }

    private suspend fun handleAddressDeepLink(address: String) {
        Log.d(TAG, "Looking up device by address: $address")

        // First try to find existing device by address
        val existingDevice = deviceRepository.findDeviceByAddress(address)
        if (existingDevice != null) {
            Log.d(TAG, "Found existing device: ${existingDevice.macAddress}")
            _deepLinkState.value = DeepLinkState.NavigateToDevice(
                NavigationEvent(existingDevice.macAddress),
            )
            return
        }

        // Device not in DB, try to discover it
        Log.d(TAG, "Device not found, attempting first contact at: $address")
        _deepLinkState.value = DeepLinkState.Loading

        @Suppress("TooGenericExceptionCaught") // Intentional: catch all network/parsing failures
        try {
            val device = deviceFirstContactService.fetchAndUpsertDevice(address)
            Log.d(TAG, "First contact successful: ${device.macAddress}")
            _deepLinkState.value = DeepLinkState.NavigateToDevice(NavigationEvent(device.macAddress))
        } catch (e: Exception) {
            Log.e(TAG, "First contact failed for address: $address", e)
            _deepLinkState.value = DeepLinkState.Error(
                messageResId = R.string.deep_link_error_unreachable,
                address = address,
            )
        }
    }

    private fun handleApModeDeepLink() {
        Log.d(TAG, "Handling AP mode deep link")
        // Navigate using the special AP mode MAC constant
        _deepLinkState.value = DeepLinkState.NavigateToDevice(NavigationEvent(AP_MODE_MAC_ADDRESS))
    }

    /**
     * Clears the deep link state after navigation has been handled.
     */
    fun clearDeepLinkState() {
        _deepLinkState.value = DeepLinkState.Idle
    }

    /**
     * Cancels the current deep link operation and clears the state.
     */
    fun cancelDeepLink() {
        deepLinkJob?.cancel()
        deepLinkJob = null
        _deepLinkState.value = DeepLinkState.Idle
    }
}
