package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState

private fun getBatteryImage(
    batteryPercentage: Double,
): Int {
    return when {
        batteryPercentage <= 10 -> R.drawable.baseline_battery_0_bar_24
        batteryPercentage <= 20 -> R.drawable.baseline_battery_1_bar_24
        batteryPercentage <= 40 -> R.drawable.baseline_battery_2_bar_24
        batteryPercentage <= 60 -> R.drawable.baseline_battery_3_bar_24
        batteryPercentage <= 70 -> R.drawable.baseline_battery_4_bar_24
        batteryPercentage <= 80 -> R.drawable.baseline_battery_5_bar_24
        batteryPercentage <= 90 -> R.drawable.baseline_battery_6_bar_24
        else -> R.drawable.baseline_battery_full_24
    }
}

@Composable
fun deviceBatteryPercentageImage(device: DeviceWithState) {
    val stateInfo by device.stateInfo
    if (stateInfo?.info?.userMods?.batteryLevel != null) {
        val batteryPercentage = stateInfo?.info?.userMods?.batteryLevel?.get(0) as? Double ?: 0.0
        Icon(
            painter = painterResource(getBatteryImage(batteryPercentage)),
            contentDescription = stringResource(R.string.battery_percentage),
            modifier = Modifier
                .height(16.dp)
                .offset(y = (-1).dp)
        )
    }
}