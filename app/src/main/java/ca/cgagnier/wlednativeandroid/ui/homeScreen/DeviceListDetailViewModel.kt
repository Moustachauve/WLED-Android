package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "DeviceListDetailViewModel"

@HiltViewModel
class DeviceListDetailViewModel @Inject constructor(
    private val preferencesRepository: UserPreferencesRepository,
    networkManager: NetworkConnectivityManager,
    private val deviceFirstContactService: DeviceFirstContactService,
    private val discoveryService: DeviceDiscovery,
) : ViewModel(),
    DefaultLifecycleObserver {
    val isWLEDCaptivePortal = networkManager.isWLEDCaptivePortal

    val showHiddenDevices = preferencesRepository.showHiddenDevices
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    private val _isAddDeviceDialogVisible = MutableStateFlow(false)
    val isAddDeviceDialogVisible: StateFlow<Boolean> = _isAddDeviceDialogVisible

    init {
        // This ensures onResume/onPause are called only when the APP goes background/foreground,
        // not when the screen rotates.
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        } catch (e: Exception) {
            Log.w(TAG, "ProcessLifecycleOwner not available: ${e.message}")
        }
        viewModelScope.launch {
            discoveryService.discoveredDevices.collect { device ->
                deviceDiscovered(device.address, device.macAddress)
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        Log.i(TAG, "App in foreground, starting discovery")
        startDiscoveryServiceTimed()
    }

    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        Log.i(TAG, "App in background, stopping discovery")
        stopDiscoveryService()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        } catch (e: Exception) {
            Log.w(TAG, "ProcessLifecycleOwner not available during onCleared: ${e.message}")
        }
        stopDiscoveryService()
    }

    private fun startDiscoveryService() {
        Log.i(TAG, "Start device discovery")
        discoveryService.start()
    }

    fun startDiscoveryServiceTimed(timeMillis: Long = 10000) = viewModelScope.launch(Dispatchers.IO) {
        Log.i(TAG, "Starting timed device discovery")
        startDiscoveryService()
        delay(timeMillis)
        stopDiscoveryService()
    }

    fun stopDiscoveryService() {
        Log.i(TAG, "Stop device discovery")
        discoveryService.stop()
    }

    private fun deviceDiscovered(address: String, macAddress: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Try the fast path (database only) first
                if (!deviceFirstContactService.tryUpdateAddress(macAddress, address)) {
                    // Fallback: Fetch info from the device (network call)
                    deviceFirstContactService.fetchAndUpsertDevice(address)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch/upsert device at $address", e)
            }
        }
    }

    fun toggleShowHiddenDevices() = viewModelScope.launch(Dispatchers.IO) {
        preferencesRepository.updateShowHiddenDevices(!showHiddenDevices.value)
    }

    fun showAddDeviceDialog() {
        _isAddDeviceDialogVisible.update {
            true
        }
    }

    fun hideAddDeviceDialog() {
        _isAddDeviceDialogVisible.update {
            false
        }
    }
}
