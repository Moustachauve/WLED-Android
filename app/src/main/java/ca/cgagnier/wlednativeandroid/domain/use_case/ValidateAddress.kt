package ca.cgagnier.wlednativeandroid.domain.use_case

import ca.cgagnier.wlednativeandroid.R
import javax.inject.Inject

class ValidateAddress @Inject constructor() {
    fun execute(address: String): ValidationResult {
        if (address.isEmpty()) {
            return ValidationResult(
                successful = false, errorMessage = R.string.please_enter_value
            )
        }
        if (address.contains(" ")) {
            return ValidationResult(
                successful = false, errorMessage = R.string.please_enter_valid_value
            )
        }
        return ValidationResult(
            successful = true
        )
    }
}