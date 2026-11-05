package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.domain.AddressValidationError
import ca.cgagnier.wlednativeandroid.domain.AddressValidationResult
import ca.cgagnier.wlednativeandroid.domain.AddressValidator
import ca.cgagnier.wlednativeandroid.service.DeviceFirstContactService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "DeviceAddViewModel"

class DeviceAddViewModel(
    private val addressValidator: AddressValidator,
    private val deviceFirstContactService: DeviceFirstContactService,
) : ViewModel() {

    var state by mutableStateOf(DeviceAddState())
    private var findDeviceJob: Job? = null

    fun setAddress(address: String) {
        if (state.step is DeviceAddStep.Form) state = state.copy(address = address)
    }

    fun submitCreateDevice() {
        findDeviceJob = viewModelScope.launch(Dispatchers.IO) {
            when (val validationResult = addressValidator.validate(state.address)) {
                is AddressValidationResult.Invalid -> {
                    val errorResId = when (validationResult.error) {
                        AddressValidationError.EMPTY -> R.string.please_enter_value
                        AddressValidationError.INVALID -> R.string.please_enter_valid_value
                    }
                    state = state.copy(
                        step = DeviceAddStep.Form(addressError = errorResId),
                    )
                }

                is AddressValidationResult.Valid -> {
                    findDevice(validationResult.sanitizedAddress)
                }
            }
        }
    }

    /**
     * Starts searching for the device and adds it, if one is found
     */
    private suspend fun findDevice(sanitizedAddress: String) {
        state = state.copy(step = DeviceAddStep.Adding)
        try {
            val newDevice = deviceFirstContactService.fetchAndUpsertDevice(sanitizedAddress)
            // If the dialog was closed before we got here, don't update the state
            if (!currentCoroutineContext().isActive) {
                return
            }
            state = state.copy(
                step = DeviceAddStep.Success(
                    device = newDevice,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error during first contact", e)
            // If the dialog was closed before we got here, don't update the state
            if (!currentCoroutineContext().isActive) {
                return
            }
            state = state.copy(
                step = DeviceAddStep.Form(
                    addressError = R.string.add_device_error,
                ),
            )
        }
    }

    /**
     * Clears the current state and cancels the find device job
     */
    fun clear() {
        // This needs to be canceled to avoid showing a "success" screen if the user
        // reopens the "add device" screen quickly after dismissing it in the "Adding" step.
        findDeviceJob?.cancel()
        state = DeviceAddState()
    }
}
