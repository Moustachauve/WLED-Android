package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.components.deviceName


@Composable
fun DeviceAdd(
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit,
    viewModel: DeviceAddViewModel = hiltViewModel(),
) {
    val state = viewModel.state

    val dismissRequest = {
        onDismissRequest()
        viewModel.clear()
    }

    Dialog(
        onDismissRequest = dismissRequest
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = modifier
                    .padding(16.dp)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .fillMaxSize()
                    .height(IntrinsicSize.Max),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.add_a_device),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                when (state.step) {
                    is DeviceAddStep.Form -> step1AddressForm(
                        state = state,
                        step = state.step,
                        viewModel = viewModel,
                        onDismissRequest = dismissRequest
                    )

                    is DeviceAddStep.Adding -> step2Loading(state)
                    is DeviceAddStep.Success -> step3Complete(
                        step = state.step, onDismissRequest = dismissRequest
                    )
                }
            }
        }
    }
}

@Composable
private fun step1AddressForm(
    state: DeviceAddState,
    step: DeviceAddStep.Form,
    viewModel: DeviceAddViewModel,
    onDismissRequest: () -> Unit,
) {
    val focusRequester = remember {
        FocusRequester()
    }
    val focusManager = LocalFocusManager.current

    LaunchedEffect("initialFocus") {
        focusRequester.requestFocus()
    }
    OutlinedTextField(
        value = state.address,
        onValueChange = {
            viewModel.setAddress(it)
        },
        label = { Text(stringResource(R.string.ip_address_or_url)) },
        isError = step.addressError != null,
        supportingText = {
            if (step.addressError != null) {
                Text(stringResource(step.addressError))
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Next,
            keyboardType = KeyboardType.Uri,
        ),
        keyboardActions = KeyboardActions(
            onNext = {
                focusManager.moveFocus(FocusDirection.Down)
            }),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )

    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = {
                onDismissRequest()
            },
        ) {
            Text(stringResource(R.string.cancel))
        }
        Button(
            onClick = {
                viewModel.submitCreateDevice()
            },
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_a_device)
            )
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
private fun step2Loading(state: DeviceAddState) {
    // TODO: Replace this by a `LoadingIndicator` once it is available/out of experimental
    CircularProgressIndicator(
        modifier = Modifier
            .height(48.dp)
            .width(48.dp),
    )
    Text(
        stringResource(R.string.add_adding_device, state.address),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(12.dp),
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

@Composable
private fun step3Complete(
    step: DeviceAddStep.Success, onDismissRequest: () -> Unit
) {
    Icon(
        modifier = Modifier
            .height(48.dp)
            .width(48.dp),
        painter = painterResource(id = R.drawable.baseline_add_task_24),
        contentDescription = stringResource(R.string.success),
        tint = Color(0xFF00b300) // Green
    )
    Text(
        stringResource(R.string.success),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp)
    )
    Text(
        stringResource(R.string.add_device_added, deviceName(step.device)),
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis
    )


    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End
    ) {
        TextButton(
            onClick = {
                onDismissRequest()
            },
        ) {
            Text(stringResource(R.string.done))
        }
    }
}