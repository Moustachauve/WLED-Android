package ca.cgagnier.wlednativeandroid.service.device

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.device.api.RequestsManager

/**
 * Stores the transient information about the state of a device
 */
data class State(val device: Device, val requestsManager: RequestsManager)