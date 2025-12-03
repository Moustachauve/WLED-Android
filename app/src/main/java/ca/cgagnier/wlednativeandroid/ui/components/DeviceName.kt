package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device

@Composable
@ReadOnlyComposable
fun deviceName(device: Device?): String {
    return device?.customName?.trim().takeIf { !it.isNullOrBlank() }
        ?: device?.originalName?.trim().takeIf { !it.isNullOrBlank() }
        ?: stringResource(R.string.default_device_name)
}