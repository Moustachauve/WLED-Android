package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SUBSCRIPTION_TIMEOUT_MS = 5000L

/**
 * UI adapter ViewModel exposing reactive device flows and delegating commands
 * to the shared [DeviceConnectionManager].
 */
class DeviceWebsocketListViewModel(
    userPreferencesRepository: UserPreferencesRepository,
    private val deviceConnectionManager: DeviceConnectionManager,
) : ViewModel() {

    val allDevicesWithState: StateFlow<List<DeviceWithState>> =
        deviceConnectionManager.allDevicesWithState

    val showOfflineDevicesLast: StateFlow<Boolean> =
        userPreferencesRepository.showOfflineDevicesLast.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = false,
        )

    val showHiddenDevices: StateFlow<Boolean> =
        userPreferencesRepository.showHiddenDevices.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = false,
        )

    val isPaused: StateFlow<Boolean> = deviceConnectionManager.isPaused

    fun setBrightness(device: DeviceWithState, brightness: Int) {
        viewModelScope.launch {
            deviceConnectionManager.setBrightness(device.device.macAddress, brightness)
        }
    }

    fun setDevicePower(device: DeviceWithState, isOn: Boolean) {
        viewModelScope.launch {
            deviceConnectionManager.setPower(device.device.macAddress, isOn)
        }
    }

    fun refreshOfflineDevices() {
        deviceConnectionManager.refreshOfflineDevices()
    }

    fun deleteDevice(device: Device) {
        viewModelScope.launch {
            deviceConnectionManager.deleteDevice(device)
        }
    }

    fun updateDeviceState(deviceWithState: DeviceWithState) {
        deviceConnectionManager.updateDeviceState(deviceWithState)
    }
}
