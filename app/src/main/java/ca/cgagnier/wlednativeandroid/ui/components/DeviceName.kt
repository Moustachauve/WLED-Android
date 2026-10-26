package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.resolveDeviceName

@Composable
@ReadOnlyComposable
fun deviceName(device: Device?): String = resolveDeviceName(device, stringResource(R.string.default_device_name))
