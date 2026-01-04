package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd

import androidx.annotation.StringRes
import ca.cgagnier.wlednativeandroid.model.Device

data class DeviceAddState(
    val address: String = "",
    val step: DeviceAddStep = DeviceAddStep.Form(addressError = null)
)

sealed class DeviceAddStep {
    data class Form(@param:StringRes val addressError: Int? = null) : DeviceAddStep()
    data object Adding : DeviceAddStep()
    data class Success(val device: Device) : DeviceAddStep()
}
