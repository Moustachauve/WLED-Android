package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceDiscovery
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import ca.cgagnier.wlednativeandroid.service.DiscoveredDevice
import ca.cgagnier.wlednativeandroid.service.NetworkConnectivityManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "DeviceListDetailViewModel"
private const val SUBSCRIPTION_TIMEOUT_MS = 5000L
private const val DEFAULT_DISCOVERY_DURATION_MS = 10_000L

@Suppress("LongParameterList") // DI constructor requires multiple dependencies
class DeviceListDetailViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    networkManager: NetworkConnectivityManager,
    private val deviceFirstContactService: DeviceFirstContactService,
    private val discoveryService: DeviceDiscovery,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val processLifecycle: Lifecycle? = null,
) : ViewModel(),
    DefaultLifecycleObserver {
    val isWLEDCaptivePortal = networkManager.isWLEDCaptivePortal

    val showHiddenDevices = preferencesRepository.showHiddenDevices
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = false,
        )

    private val _isAddDeviceDialogVisible = MutableStateFlow(false)
    val isAddDeviceDialogVisible: StateFlow<Boolean> = _isAddDeviceDialogVisible

    private var timedDiscoveryJob: Job? = null

    init {
        // This ensures onResume/onPause are called only when the APP goes background/foreground,
        // not when the screen rotates.
        processLifecycle?.addObserver(this)
        viewModelScope.launch {
            discoveryService.discoveredDevices.collect { device ->
                deviceDiscovered(device)
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
        processLifecycle?.removeObserver(this)
        stopDiscoveryService()
    }

    private fun startDiscoveryService() {
        Log.i(TAG, "Start device discovery")
        discoveryService.start()
    }

    fun startDiscoveryServiceTimed(timeMillis: Long = DEFAULT_DISCOVERY_DURATION_MS): Job {
        timedDiscoveryJob?.cancel()
        val job = viewModelScope.launch(ioDispatcher) {
            Log.i(TAG, "Starting timed device discovery")
            startDiscoveryService()
            delay(timeMillis)
            Log.i(TAG, "Timed device discovery elapsed, stopping discovery")
            discoveryService.stop()
        }
        timedDiscoveryJob = job
        return job
    }

    fun stopDiscoveryService() {
        Log.i(TAG, "Stop device discovery")
        timedDiscoveryJob?.cancel()
        timedDiscoveryJob = null
        discoveryService.stop()
    }

    private fun deviceDiscovered(device: DiscoveredDevice) {
        viewModelScope.launch(ioDispatcher) {
            deviceFirstContactService.processDiscoveredDevice(device)
        }
    }

    fun toggleShowHiddenDevices() = viewModelScope.launch(ioDispatcher) {
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
