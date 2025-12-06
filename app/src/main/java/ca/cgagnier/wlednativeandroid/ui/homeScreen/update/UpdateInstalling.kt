package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.preview.getPreviewDevice
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme

@Composable
fun UpdateInstallingDialog(
    device: DeviceWithState,
    version: VersionWithAssets,
    onDismiss: () -> Unit,
    viewModel: UpdateInstallingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(device.device.address) {
        viewModel.startUpdate(device, version, context.cacheDir)
    }

    UpdateInstallingDialog(
        state = state,
        device = device,
        onDismiss = {
            viewModel.resetState()
            onDismiss()
        },
        onToggleErrorMessage = {
            viewModel.toggleErrorMessage()
        },
    )
}

@Composable
fun UpdateInstallingDialog(
    state: UpdateInstallingState,
    device: DeviceWithState,
    onDismiss: () -> Unit,
    onToggleErrorMessage: () -> Unit,
) {
    AlertDialog(
        title = {
            Text(
                stringResource(
                    R.string.updating,
                    deviceName(device.device)
                )
            )
        },
        text = {
            UpdateDialogContent(state = state)
        },
        onDismissRequest = {
            if (state.canDismiss) {
                onDismiss()
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                },
                enabled = state.canDismiss
            ) {
                if (state.step is UpdateInstallingStep.Done || state.step is UpdateInstallingStep.Error) {
                    Text(stringResource(R.string.done))
                } else {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        dismissButton = {
            if (state.step is UpdateInstallingStep.Error) {
                val text = if (state.step.showError) R.string.hide_error else R.string.show_error
                TextButton(
                    onClick = {
                        onToggleErrorMessage()
                    },
                ) {
                    Text(stringResource(text))
                }
            }
        },
        icon = {},
    )
}

@Composable
fun UpdateDialogContent(state: UpdateInstallingState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .width(IntrinsicSize.Min),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UpdateInstallingStatus(
            modifier = Modifier
                .height(48.dp)
                .width(48.dp),
            step = state.step
        )
        Text(
            updateInstallingStatusMessage(step = state.step),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 12.dp)
        )
        Text(state.assetName)
        AnimatedVisibility (!state.canDismiss) {
            Text(
                stringResource(R.string.please_do_not_close_the_app_or_turn_off_the_device),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        AnimatedVisibility(state.step is UpdateInstallingStep.NoCompatibleVersion) {
            ErrorMessageCard(stringResource(R.string.no_compatible_version_found_details))
        }
        AnimatedVisibility(state.step is UpdateInstallingStep.Error && state.step.showError) {
            val step = state.step as? UpdateInstallingStep.Error
            ErrorMessageCard(step?.error ?: "Unknown error")
        }
    }
}

@Composable
private fun UpdateInstallingStatus(
    modifier: Modifier = Modifier,
    step: UpdateInstallingStep
) {
    when (step) {
        is UpdateInstallingStep.Starting -> CircularProgressIndicator(modifier)
        is UpdateInstallingStep.Downloading -> CircularProgressIndicator(
            modifier = modifier,
            progress = { step.progress / 100f }
        )
        is UpdateInstallingStep.Installing -> CircularProgressIndicator(modifier)
        is UpdateInstallingStep.Error, is UpdateInstallingStep.NoCompatibleVersion -> Icon(
            modifier = modifier,
            painter = painterResource(R.drawable.baseline_error_outline_24),
            contentDescription = stringResource(R.string.update_failed),
            tint = MaterialTheme.colorScheme.error
        )
        is UpdateInstallingStep.Done -> Icon(
            modifier = modifier,
            painter = painterResource(R.drawable.ic_twotone_check_circle_outline_24),
            contentDescription = stringResource(R.string.update_completed),
            tint = Color(0xFF00b300) // Green
        )
    }
}

@Composable
private fun updateInstallingStatusMessage(
    step: UpdateInstallingStep
): String {
    return when (step) {
        is UpdateInstallingStep.Starting -> stringResource(R.string.starting_up)
        is UpdateInstallingStep.Downloading -> stringResource(R.string.downloading_version)
        is UpdateInstallingStep.Installing -> stringResource(R.string.installing_update)
        is UpdateInstallingStep.NoCompatibleVersion -> stringResource(R.string.no_compatible_version_found)
        is UpdateInstallingStep.Error -> stringResource(R.string.update_failed)
        is UpdateInstallingStep.Done -> stringResource(R.string.update_completed)
    }
}

@Composable
private fun ErrorMessageCard(errorMessage: String) {
    Card(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .width(IntrinsicSize.Min),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            errorMessage,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .width(IntrinsicSize.Min),
        )
    }
}

class SampleStateStepProvider : PreviewParameterProvider<UpdateInstallingState> {
    override val values = sequenceOf(
        UpdateInstallingState(
            step = UpdateInstallingStep.Starting,
            canDismiss = true
        ),
        UpdateInstallingState(
            step = UpdateInstallingStep.Downloading(progress = 50),
            canDismiss = true,
            assetName = "WLED-1.2.3-WOW.bin"
        ),
        UpdateInstallingState(
            step = UpdateInstallingStep.Installing,
            canDismiss = false,
            assetName = "WLED-1.2.3-WOW.bin"
        ),
        UpdateInstallingState(
            step = UpdateInstallingStep.NoCompatibleVersion,
            canDismiss = true,
            assetName = "WLED-1.2.3-WOW.bin"
        ),
        UpdateInstallingState(
            step = UpdateInstallingStep.Error("Something went wrong", showError = false),
            canDismiss = true,
            assetName = "WLED-1.2.3-WOW.bin"
        ),
        UpdateInstallingState(
            step = UpdateInstallingStep.Error("Something went wrong", showError = true),
            canDismiss = true,
            assetName = "WLED-1.2.3-WOW.bin"
        ),
        UpdateInstallingState(
            step = UpdateInstallingStep.Done,
            canDismiss = true,
            assetName = "WLED-1.2.3-WOW.bin"
        ),
    )
}

@Preview(showBackground = true)
@Composable
fun UpdateInstallingDialogStepStartingPreview(
    @PreviewParameter(SampleStateStepProvider::class) state: UpdateInstallingState
) {
    WLEDNativeTheme(darkTheme = isSystemInDarkTheme()) {
        UpdateInstallingDialog(
            state = state,
            device = getPreviewDevice(),
            onDismiss = {},
            onToggleErrorMessage = {}
        )
    }
}