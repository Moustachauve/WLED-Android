package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.StatefulDevice
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.theme.DeviceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "screen_DeviceList"

@Composable
fun DeviceList(
    selectedDevice: DeviceWithState?,
    isWLEDCaptivePortal: Boolean = false,
    onItemClick: (DeviceWithState) -> Unit,
    onItemEdit: (DeviceWithState) -> Unit,
    onAddDevice: () -> Unit,
    onShowHiddenDevices: () -> Unit,
    onRefresh: () -> Unit,
    onOpenDrawer: () -> Unit,
    viewModel: DeviceListViewModel = hiltViewModel(),
    deviceWebsocketListViewModel: DeviceWebsocketListViewModel = hiltViewModel(),
) {
    val devices by deviceWebsocketListViewModel.devicesWithState.collectAsStateWithLifecycle()
    val shouldShowDevicesAreHidden by deviceWebsocketListViewModel.shouldShowDevicesAreHidden.collectAsStateWithLifecycle()

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val refresh: () -> Unit = {
        isRefreshing = true
        onRefresh()
        coroutineScope.launch {
            delay(1800)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            DeviceListAppBar(
                onOpenDrawer = onOpenDrawer,
                onAddDevice = onAddDevice,
            )
        },
    ) { innerPadding ->
        PullToRefreshBox(
            modifier = Modifier.padding(innerPadding),
            state = pullToRefreshState,
            isRefreshing = isRefreshing,
            onRefresh = refresh,
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .clip(shape = MaterialTheme.shapes.large),
            ) {
                if (devices.isEmpty()) {
                    item {
                        NoDevicesItem(
                            modifier = Modifier.fillParentMaxSize(),
                            shouldShowHiddenDevices = shouldShowDevicesAreHidden,
                            onAddDevice = onAddDevice,
                            onShowHiddenDevices = onShowHiddenDevices
                        )
                    }
                } else {
                    // TODO: Add support back for captive portal
                    //if (isWLEDCaptivePortal) {
                    //    item {
                    //        val device = StatefulDevice.getDefaultAPDevice()
                    //        DeviceAPListItem(
                    //            isSelected = device.address == selectedDevice?.address,
                    //            onClick = { onItemClick(device) },
                    //            modifier = Modifier.animateItem()
                    //        )
                    //    }
                    //}
                    itemsIndexed(
                        devices,
                        key = { _, device -> device.device.macAddress }) { _, device ->
                        var isConfirmingDelete by remember { mutableStateOf(false) }
                        val swipeDismissState = rememberSwipeToDismissBoxState(
                            positionalThreshold = { distance -> distance * 0.3f },
                        )
                        DeviceListItem(
                            device = device,
                            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
                            onClick = { onItemClick(device) },
                            swipeToDismissBoxState = swipeDismissState,
                            onDismiss = { direction ->
                                if (direction == SwipeToDismissBoxValue.EndToStart) {
                                    isConfirmingDelete = true
                                } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                                    coroutineScope.launch {
                                        swipeDismissState.reset()
                                        onItemEdit(device)
                                    }
                                }
                            },
                            onPowerSwitchToggle = { isOn ->
                                deviceWebsocketListViewModel.setDevicePower(device, isOn)
                            },
                            onBrightnessChanged = { brightness ->
                                deviceWebsocketListViewModel.setBrightness(device, brightness)
                            },
                            modifier = Modifier.animateItem()
                        )
                        LaunchedEffect(isConfirmingDelete) {
                            if (!isConfirmingDelete) {
                                swipeDismissState.reset()
                            }
                        }

                        if (isConfirmingDelete) {
                            ConfirmDeleteDialog(
                                device = device,
                                onConfirm = {
                                    coroutineScope.launch {
                                        swipeDismissState.reset()
                                        isConfirmingDelete = false
                                        viewModel.deleteDevice(device.device)
                                    }
                                },
                                onDismiss = {
                                    isConfirmingDelete = false
                                }
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.padding(42.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceListAppBar(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit,
    onAddDevice: () -> Unit,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Image(
                painter = painterResource(id = R.drawable.wled_logo_akemi),
                contentDescription = stringResource(R.string.app_logo)
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = stringResource(R.string.description_menu_button)
                )
            }
        },
        actions = {
            IconButton(onClick = onAddDevice) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_a_device)
                )
            }
        }
    )
}

@Composable
fun ConfirmDeleteDialog(
    device: DeviceWithState? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    device?.let {
        AlertDialog(
            title = {
                Text(text = stringResource(R.string.deleting_device))
            },
            text = {
                DeviceTheme(device) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        DeviceInfoTwoRows(
                            modifier = Modifier.padding(16.dp),
                            device = device
                        )
                    }
                }
            },
            onDismissRequest = {
                onDismiss()
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onConfirm()
                    }
                ) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
