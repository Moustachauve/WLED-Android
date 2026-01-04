package ca.cgagnier.wlednativeandroid.domain.use_case

import androidx.annotation.StringRes

data class ValidationResult(
    val successful: Boolean,
    @StringRes val errorMessage: Int? = null
)