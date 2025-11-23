package ca.cgagnier.wlednativeandroid.service.websocket

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.MutableLiveData
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo

class DeviceWithState(initialDevice: Device) {
    var device: Device by mutableStateOf(initialDevice)
    val stateInfo: MutableState<DeviceStateInfo?> = mutableStateOf(null)
    val isWebsocketConnected: MutableState<Boolean> = mutableStateOf(false)
}