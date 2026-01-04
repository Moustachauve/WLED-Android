package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R


@Composable
fun DeviceAPListItem(
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
) {
    val cardColor =
        if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest

    OutlinedCard(
        modifier = modifier
            .padding(bottom = 16.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = cardColor,
        ),
        shape = MaterialTheme.shapes.extraLarge,
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_router_24),
                    contentDescription = stringResource(R.string.device_ap_mode_detected),
                    modifier = Modifier.padding(end = 16.dp)
                )
                Column {
                    Text(
                        stringResource(R.string.device_ap_mode_detected),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.device_ap_mode_detected_click),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}