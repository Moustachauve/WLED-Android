package ca.cgagnier.wlednativeandroid.domain.usecase

import androidx.annotation.StringRes

data class ValidationResult(
    val successful: Boolean,
    @StringRes val errorMessage: Int? = null,
)
