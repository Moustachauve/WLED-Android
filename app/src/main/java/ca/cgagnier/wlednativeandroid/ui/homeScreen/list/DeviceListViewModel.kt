package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.util.Log
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.StatefulDevice
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.repository.DeviceRepository
import ca.cgagnier.wlednativeandroid.repository.UserPreferencesRepository
import ca.cgagnier.wlednativeandroid.service.device.StateFactory
import ca.cgagnier.wlednativeandroid.service.device.api.request.StateChangeRequest
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.WhileSubscribed
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject


private const val TAG = "DeviceListViewModel"

@HiltViewModel
class DeviceListViewModel @Inject constructor(
    private val repository: DeviceRepository,
    private val stateFactory: StateFactory,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {
    private val showHiddenDevices = preferencesRepository.showHiddenDevices
    private val showOfflineDevicesLast = preferencesRepository.showOfflineDevicesLast

    private val _uiState = MutableStateFlow(DeviceListUiState())
    val uiState: StateFlow<DeviceListUiState> = _uiState
        .combine(showHiddenDevices) { state, showHiddenDevices ->
            state.copy(showHiddenDevices = showHiddenDevices)
        }
        .combine(showOfflineDevicesLast) { state, showOfflineDevicesLast ->
            state.copy(showOfflineDevicesLast = showOfflineDevicesLast)
        }
        .stateIn(
            scope = viewModelScope,
            started = WhileSubscribed(5000),
            initialValue = DeviceListUiState()
        )

    // TODO: Load devices here and apply filters (hidden devices, offline devices last, ...)
    val devices: List<DeviceWithState> = listOf(
        DeviceWithState(
            device = Device(
                "abc",
                "192.168.11.110",
                false,
                "original name",
                "custom name"
            )
        )
    )

    fun deleteDevice(device: Device) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Deleting device ${device.originalName} - ${device.address}")
            repository.delete(device)
        }
    }
}

@Stable
data class DeviceListUiState(
    val showOfflineDevicesLast: Boolean = true,
    val showHiddenDevices: Boolean = false,
    val listState: LazyListState = LazyListState(),
)
