package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

private fun getNetworkStrenghtImage(
    networkRssi: Int,
    isOnline: Boolean
): Int {
    if (!isOnline) {
        return R.drawable.twotone_signal_wifi_connected_no_internet_0_24
    }
    if (networkRssi >= -50) {
        return R.drawable.twotone_signal_wifi_4_bar_24
    }
    if (networkRssi >= -70) {
        return R.drawable.twotone_signal_wifi_3_bar_24
    }
    if (networkRssi >= -80) {
        return R.drawable.twotone_signal_wifi_2_bar_24
    }
    if (networkRssi >= -100) {
        return R.drawable.twotone_signal_wifi_1_bar_24
    }
    return R.drawable.twotone_signal_wifi_0_bar_24
}

@Composable
fun deviceNetworkStrengthImage(device: DeviceWithState) {
    val stateInfo by device.stateInfo
    val rssi = stateInfo?.info?.wifi?.rssi ?: -101
    TooltipBox(
        positionProvider = rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip {
                if (device.isOnline) {
                    Text(stringResource(R.string.signal_strength, rssi))
                } else {
                    Text(
                        stringResource(
                            R.string.signal_strength,
                            stringResource(R.string.is_offline)
                        )
                    )
                }
            }
        },
        state = rememberTooltipState()
    ) {
        Icon(
            painter = painterResource(
                getNetworkStrenghtImage(
                    rssi,
                    device.isOnline
                )
            ),
            contentDescription = stringResource(R.string.network_status),
            modifier = Modifier
                .padding(start = 4.dp)
                .height(16.dp)
                .offset(y = (-2).dp)
        )
    }
}