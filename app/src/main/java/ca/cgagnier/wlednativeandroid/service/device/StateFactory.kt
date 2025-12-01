package ca.cgagnier.wlednativeandroid.service.device

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.device.api.JsonApiRequestHandler
import ca.cgagnier.wlednativeandroid.service.device.api.RequestsManager

class StateFactory(private val requestHandler: JsonApiRequestHandler) {

    private var allStates = mutableMapOf<String, State>()

    fun getState(device: Device): State {
        if (!allStates.contains(device.address)) {
            allStates[device.address] = State(
                device,
                RequestsManager(device.macAddress, requestHandler)
            )
        }

        return allStates[device.address]!!
    }
}