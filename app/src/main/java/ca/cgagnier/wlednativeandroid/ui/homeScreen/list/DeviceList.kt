package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
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
    viewModel: DeviceWebsocketListViewModel = hiltViewModel(),
) {
    val allDevices by viewModel.allDevicesWithState.collectAsStateWithLifecycle()
    val showOfflineDevicesLast by viewModel.showOfflineDevicesLast.collectAsStateWithLifecycle()

    val visibleDevices by remember(allDevices) {
        derivedStateOf {
            allDevices.filter { !it.device.isHidden || viewModel.showHiddenDevices.value }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                    it.device.customName.ifBlank { it.device.originalName }
                })
        }
    }

    val onlineDevices by remember(visibleDevices) {
        derivedStateOf {
            visibleDevices.filter { it.isWebsocketConnected.value }
        }
    }
    val offlineDevices by remember(visibleDevices) {
        derivedStateOf {
            visibleDevices.filter { !it.isWebsocketConnected.value }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, viewModel) {
        lifecycleOwner.lifecycle.addObserver(viewModel)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(viewModel)
        }
    }

    val refresh: () -> Unit = {
        isRefreshing = true
        onRefresh()
        viewModel.refreshOfflineDevices()
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
                if (allDevices.isEmpty()) {
                    item {
                        NoDevicesItem(
                            modifier = Modifier.fillParentMaxSize(),
                            shouldShowHiddenDevices = visibleDevices.isEmpty() && allDevices.isNotEmpty(),
                            onAddDevice = onAddDevice,
                            onShowHiddenDevices = onShowHiddenDevices
                        )
                    }
                } else {
                    if (isWLEDCaptivePortal) {
                        item {
                            val device = getApModeDeviceWithState()
                            DeviceAPListItem(
                                isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
                                onClick = { onItemClick(device) },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                    if (showOfflineDevicesLast) {
                        onlineOfflineDevicesList(
                            onlineDevices = onlineDevices,
                            offlineDevices = offlineDevices,
                            selectedDevice = selectedDevice,
                            onItemClick = onItemClick,
                            onItemEdit = onItemEdit,
                            viewModel = viewModel
                        )
                    } else {
                        allDevicesList(
                            devices = visibleDevices,
                            selectedDevice = selectedDevice,
                            onItemClick = onItemClick,
                            onItemEdit = onItemEdit,
                            viewModel = viewModel
                        )
                    }

                    // This spacer is so that the last item of the list can be scrolled a bit
                    // further than just the bottom of the screen.
                    item {
                        Spacer(Modifier.padding(42.dp))
                    }
                }
            }
        }
    }
}

fun LazyListScope.allDevicesList(
    devices: List<DeviceWithState>,
    selectedDevice: DeviceWithState?,
    onItemClick: (DeviceWithState) -> Unit,
    onItemEdit: (DeviceWithState) -> Unit,
    viewModel: DeviceWebsocketListViewModel
) {
    itemsIndexed(
        devices, key = { _, device -> device.device.macAddress }) { _, device ->

        DeviceRow(
            device = device,
            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
            onClick = onItemClick,
            onEdit = onItemEdit,
            viewModel = viewModel
        )
    }
}

fun LazyListScope.onlineOfflineDevicesList(
    onlineDevices: List<DeviceWithState>,
    offlineDevices: List<DeviceWithState>,
    selectedDevice: DeviceWithState?,
    onItemClick: (DeviceWithState) -> Unit,
    onItemEdit: (DeviceWithState) -> Unit,
    viewModel: DeviceWebsocketListViewModel
) {
    itemsIndexed(
        onlineDevices, key = { _, device -> device.device.macAddress }) { _, device ->

        DeviceRow(
            device = device,
            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
            onClick = onItemClick,
            onEdit = onItemEdit,
            viewModel = viewModel
        )
    }
    if (offlineDevices.isNotEmpty()) {
        item {
            Text(stringResource(R.string.offline_devices))
        }
        itemsIndexed(
            offlineDevices, key = { _, device -> device.device.macAddress }) { _, device ->

            DeviceRow(
                device = device,
                isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
                onClick = onItemClick,
                onEdit = onItemEdit,
                viewModel = viewModel
            )
        }
    }
}

@Composable
fun LazyItemScope.DeviceRow(
    device: DeviceWithState,
    isSelected: Boolean,
    onClick: (DeviceWithState) -> Unit,
    onEdit: (DeviceWithState) -> Unit,
    viewModel: DeviceWebsocketListViewModel
) {
    val coroutineScope = rememberCoroutineScope()
    var isConfirmingDelete by remember { mutableStateOf(false) }
    val swipeDismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * 0.3f },
    )

    DeviceListItem(
        device = device,
        isSelected = isSelected,
        onClick = { onClick(device) },
        swipeToDismissBoxState = swipeDismissState,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                isConfirmingDelete = true
            } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                coroutineScope.launch {
                    swipeDismissState.reset()
                    onEdit(device)
                }
            }
        },
        onPowerSwitchToggle = { isOn ->
            viewModel.setDevicePower(device, isOn)
        },
        onBrightnessChanged = { brightness ->
            viewModel.setBrightness(device, brightness)
        },
        modifier = Modifier.animateItem()
    )
    LaunchedEffect(isConfirmingDelete) {
        if (!isConfirmingDelete) {
            swipeDismissState.reset()
        }
    }

    if (isConfirmingDelete) {
        ConfirmDeleteDialog(device = device, onConfirm = {
            coroutineScope.launch {
                swipeDismissState.reset()
                isConfirmingDelete = false
                viewModel.deleteDevice(device.device)
            }
        }, onDismiss = {
            isConfirmingDelete = false
        })
    }
}

@Composable
fun DeviceListAppBar(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit,
    onAddDevice: () -> Unit,
) {
    CenterAlignedTopAppBar(modifier = modifier, title = {
        Image(
            painter = painterResource(id = R.drawable.wled_logo_akemi),
            contentDescription = stringResource(R.string.app_logo)
        )
    }, navigationIcon = {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.description_menu_button)
            )
        }
    }, actions = {
        IconButton(onClick = onAddDevice) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_a_device)
            )
        }
    })
}

@Composable
fun ConfirmDeleteDialog(
    device: DeviceWithState? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    device?.let {
        AlertDialog(title = {
            Text(text = stringResource(R.string.deleting_device))
        }, text = {
            DeviceTheme(device) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    DeviceInfoTwoRows(
                        modifier = Modifier.padding(16.dp), device = device
                    )
                }
            }
        }, onDismissRequest = {
            onDismiss()
        }, confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                }) {
                Text(
                    stringResource(R.string.delete), color = MaterialTheme.colorScheme.error
                )
            }
        }, dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                }) {
                Text(stringResource(R.string.cancel))
            }
        })
    }
}
