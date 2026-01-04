package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme

@Composable
fun UpdateDisclaimerDialog(
    onDismiss: () -> Unit = {},
    onAccept: () -> Unit = {},
) {
    AlertDialog(
        title = {
            Text(stringResource(R.string.are_you_sure))
        },
        text = {
            UpdateDisclaimerDialogContent()
        },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onAccept,
            ) {
                Text(stringResource(R.string.install_update))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) {
                Text(stringResource(R.string.cancel))
            }
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = stringResource(R.string.are_you_sure)
            )
        },
    )
}

@Composable
private fun UpdateDisclaimerDialogContent() {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState())
    ) {
        Text(
            stringResource(R.string.device_update_disclaimer_part1)
        )
        Text(
            stringResource(R.string.device_update_disclaimer_part2),
            modifier = Modifier.padding(top = 5.dp)
        )
        Text(
            stringResource(R.string.device_update_disclaimer_part3),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 5.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
fun UpdateDisclaimerDialogPreview() {
    WLEDNativeTheme(darkTheme = isSystemInDarkTheme()) {
        UpdateDisclaimerDialog()
    }
}