package ca.cgagnier.wlednativeandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState


@Composable
fun DeviceInfoTwoRows(
    modifier: Modifier = Modifier,
    device: DeviceWithState,
    nameMaxLines: Int = 2,
) {
    val updateTag by device.updateVersionTagFlow.collectAsState(initial = null)

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                deviceName(device.device),
                style = MaterialTheme.typography.titleLarge,
                maxLines = nameMaxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .padding(bottom = 2.dp)
                .width(IntrinsicSize.Min),
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                device.device.address,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .width(IntrinsicSize.Max)
            )
            deviceNetworkStrengthImage(device)
            deviceBatteryPercentageImage(device)

            // TODO: Add websocket connection status indicator
            if (updateTag != null) {
                Icon(
                    painter = painterResource(R.drawable.baseline_download_24),
                    contentDescription = stringResource(R.string.network_status),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .height(20.dp)
                )
            }
            if (!device.isOnline) {
                Text(
                    stringResource(R.string.is_offline),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
            if (device.device.isHidden) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_visibility_off_24),
                    contentDescription = stringResource(R.string.description_back_button),
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .height(16.dp)
                )
                Text(
                    stringResource(R.string.hidden_status),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

        }
    }
}