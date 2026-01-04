package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DeviceVisibleSwitch
import ca.cgagnier.wlednativeandroid.ui.components.deviceName
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDetailsDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateDisclaimerDialog
import ca.cgagnier.wlednativeandroid.ui.homeScreen.update.UpdateInstallingDialog
import kotlinx.coroutines.delay

@Composable
fun DeviceEdit(
    device: DeviceWithState,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    viewModel: DeviceEditViewModel = hiltViewModel()
) {
    val options = listOf(
        Pair(Branch.STABLE, stringResource(R.string.stable)),
        Pair(Branch.BETA, stringResource(R.string.beta)),
    )

    val updateTag by device.updateVersionTagFlow.collectAsState(initial = null)

    val updateDetailsVersion by viewModel.updateDetailsVersion.collectAsState()
    val updateDisclaimerVersion by viewModel.updateDisclaimerVersion.collectAsState()
    val updateInstallVersion by viewModel.updateInstallVersion.collectAsState()
    val isCheckingUpdates by viewModel.isCheckingUpdates.collectAsState()

    Scaffold(
        topBar = {
            DeviceEditAppBar(
                device = device,
                canNavigateBack = canNavigateBack,
                navigateUp = navigateUp,
            )
        }
    ) { innerPadding ->
        Card(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 3.dp)
                .fillMaxHeight()
                .height(IntrinsicSize.Max),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                OutlinedTextField(
                    value = device.device.address,
                    enabled = false,
                    onValueChange = {},
                    label = { Text(stringResource(R.string.ip_address_or_url)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                )
                CustomNameTextField(device.device) {
                    viewModel.updateCustomName(device.device, it)
                }
                DeviceVisibleSwitch(
                    modifier = Modifier.padding(top = 4.dp),
                    isHidden = device.device.isHidden,
                    onCheckedChange = {
                        viewModel.updateDeviceHidden(device.device, it)
                    }
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.update_channel),
                        maxLines = 2,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    SingleChoiceSegmentedButtonRow {
                        options.forEachIndexed { index, option ->
                            SegmentedButton(
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                ),
                                onClick = {
                                    viewModel.updateDeviceBranch(
                                        device.device,
                                        option.first,
                                    )
                                },
                                selected = option.first == device.device.branch
                            ) {
                                Text(text = option.second)
                            }
                        }
                    }
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth()
                    ) {
                        // Create a local immutable copy of the variable to please the compiler
                        // type check in the condition below.
                        val currentUpdateTag = updateTag
                        if (currentUpdateTag != null) {
                            UpdateAvailable(
                                device,
                                currentUpdateTag,
                                seeUpdateDetails = {
                                    viewModel.showUpdateDetails(currentUpdateTag)
                                }
                            )
                        } else {
                            NoUpdateAvailable(
                                device,
                                isCheckingUpdates,
                                checkForUpdate = {
                                    viewModel.checkForUpdates(device.device)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    stringResource(R.string.mac_address_present, device.device.macAddress),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    updateDetailsVersion?.let { versionDetails ->
        UpdateDetailsDialog(
            device = device,
            version = versionDetails,
            onDismiss = {
                viewModel.hideUpdateDetails()
            },
            onInstall = { versionInstall ->
                viewModel.hideUpdateDetails()
                viewModel.showUpdateDisclaimer(versionInstall)
            },
            onSkip = {
                viewModel.skipUpdate(device.device, versionDetails)
            }
        )
    }
    updateDisclaimerVersion?.let { versionDisclaimer ->
        UpdateDisclaimerDialog(
            onDismiss = {
                viewModel.hideUpdateDisclaimer()
            },
            onAccept = {
                viewModel.hideUpdateDisclaimer()
                viewModel.startUpdateInstall(versionDisclaimer)
            }
        )
    }
    updateInstallVersion?.let { version ->
        UpdateInstallingDialog(
            device = device,
            version = version,
            onDismiss = {
                viewModel.stopUpdateInstall()
            },
        )
    }
}

@Composable
private fun CustomNameTextField(
    device: Device,
    onValueChange: (String) -> Unit
) {
    val deviceName = device.customName
    var inputText by remember { mutableStateOf(TextFieldValue(deviceName)) }
    OutlinedTextField(
        value = inputText,
        onValueChange = {
            inputText = it
        },
        label = { Text(stringResource(R.string.custom_name)) },
        supportingText = { Text(stringResource(R.string.leave_this_empty_to_use_the_device_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Done
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    )
    // Only save after changes and after typing has stopped for at least 0.8 seconds
    LaunchedEffect(key1 = inputText.text) {
        if (deviceName == inputText.text)
            return@LaunchedEffect
        delay(800)
        onValueChange(inputText.text)
    }
}

@Composable
fun DeviceEditAppBar(
    modifier: Modifier = Modifier,
    device: DeviceWithState,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.edit_device_with_name, deviceName(device.device)),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        modifier = modifier,
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.description_back_button)
                    )
                }
            }
        },
    )
}

@Composable
fun NoUpdateAvailable(
    device: DeviceWithState,
    isCheckingUpdates: Boolean,
    checkForUpdate: () -> Unit
) {
    Text(
        stringResource(R.string.your_device_is_up_to_date),
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        stringResource(R.string.version_v_num, device.stateInfo.value?.info?.version ?: "<?>"),
        style = MaterialTheme.typography.bodyMedium
    )
    OutlinedButton(onClick = checkForUpdate) {
        val buttonText =
            if (isCheckingUpdates) stringResource(R.string.checking_progress_update)
            else stringResource(R.string.check_for_update)
        AnimatedContent(
            targetState = buttonText,
            transitionSpec = {
                scaleIn() togetherWith fadeOut()
            },
            label = ""
        ) {
            Text(it)
        }

        AnimatedVisibility(
            visible = isCheckingUpdates
        ) {
            val size = (MaterialTheme.typography.titleSmall.lineHeight.value - 4)
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = 10.dp)
                    .padding(bottom = 2.dp)
                    .width(size.dp)
                    .height(size.dp),
            )
        }
    }
}

@Composable
fun UpdateAvailable(device: DeviceWithState, updateTag: String, seeUpdateDetails: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            modifier = Modifier
                .padding(8.dp)
                .padding(end = 10.dp),
            painter = painterResource(R.drawable.baseline_download_24),
            contentDescription = stringResource(R.string.update_available),
        )
        Column {
            Text(
                stringResource(R.string.update_available),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.from_version_to_version,
                    device.stateInfo.value?.info?.version
                        ?: "<?>",
                    updateTag
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(
                modifier = Modifier.padding(top = 6.dp),
                onClick = seeUpdateDetails
            ) {
                Text(stringResource(R.string.see_update_details))
            }
        }
    }
}