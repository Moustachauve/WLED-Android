package ca.cgagnier.wlednativeandroid.ui.components

import androidx.annotation.DrawableRes
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.WifiSignalLevel
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

@DrawableRes
internal fun WifiSignalLevel.toIconRes(): Int = when (this) {
    WifiSignalLevel.OFFLINE -> R.drawable.twotone_signal_wifi_off_24
    WifiSignalLevel.GOOD -> R.drawable.twotone_signal_wifi_3_bar_24
    WifiSignalLevel.FAIR -> R.drawable.twotone_signal_wifi_2_bar_24
    WifiSignalLevel.WEAK -> R.drawable.twotone_signal_wifi_1_bar_24
    WifiSignalLevel.NONE -> R.drawable.twotone_signal_wifi_0_bar_24
}

@Composable
fun deviceNetworkStrengthImage(device: DeviceWithState) {
    val stateInfo = device.stateInfo
    val rssi = stateInfo?.info?.wifi?.rssi ?: -101
    val signalLevel = WifiSignalLevel.fromRssi(
        rssi = rssi,
        isOnline = device.isOnline,
        defaultWhenNull = WifiSignalLevel.NONE,
    )
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
                            stringResource(R.string.is_offline),
                        ),
                    )
                }
            }
        },
        state = rememberTooltipState(),
    ) {
        Icon(
            painter = painterResource(signalLevel.toIconRes()),
            contentDescription = stringResource(R.string.network_status),
            modifier = Modifier
                .padding(start = 4.dp)
                .height(16.dp)
                .offset(y = (-2).dp),
        )
    }
}
