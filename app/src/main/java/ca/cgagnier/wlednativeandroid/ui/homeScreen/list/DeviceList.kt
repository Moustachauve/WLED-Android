package ca.cgagnier.wlednativeandroid.ui.homeScreen.list

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DeviceInfoTwoRows
import ca.cgagnier.wlednativeandroid.ui.theme.DeviceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "screen_DeviceList"

/**
 * Amount of time after a device becomes offline before it is considered offline.
 */
private const val DEVICE_OFFLINE_TIMEOUT_MS = 60000L

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
    val showHiddenDevices by viewModel.showHiddenDevices.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    var isInitialLoading by rememberSaveable { mutableStateOf(true) }

    // Keep track of the time to update the list of online/offline devices based on lastSeen
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        if (isInitialLoading) {
            launch {
                // "No Devices" Flash prevention: Wait 500ms before allowing the "No Devices" item to show.
                // If the DB loads faster than this, the user sees the list immediately.
                // If the DB is empty, the user sees a white screen for 0.5s, then the message.
                Log.d(TAG, "Initial loading!")
                delay(500)
                isInitialLoading = false
            }
        }
        while (true) {
            delay(5000)
            currentTime = System.currentTimeMillis()
        }
    }

    val visibleDevices = remember(allDevices, showHiddenDevices) {
        allDevices.filter { !it.device.isHidden || showHiddenDevices }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) {
                it.device.customName.ifBlank { it.device.originalName }
            })
    }
    // DerivedStateOf is necessary so that property changes (like websocketStatus) are also tracked.
    val partitionedDevices by remember(visibleDevices, currentTime) {
        derivedStateOf {
            visibleDevices.partition { device ->
                !shouldShowAsOffline(device, currentTime)
            }
        }
    }
    val onlineDevices = partitionedDevices.first
    val offlineDevices = partitionedDevices.second

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

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
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp)
                    .clip(shape = MaterialTheme.shapes.large),
            ) {
                // First invisible item to keep the scroll at the top if the list changes under it.
                // This is a "hack" suggested here: https://issuetracker.google.com/issues/234223556#comment2
                item(key = "first_invisible") {
                    Spacer(
                        Modifier
                            .padding(1.dp)
                            .height(0.dp)
                    )
                }
                if (visibleDevices.isEmpty() && !isWLEDCaptivePortal) {
                    // Don't show the empty page during the initial load to improve the user
                    // experience.
                    if (isInitialLoading) {
                        items(3) {
                            SkeletonDeviceRow()
                        }
                    } else {
                        item(key = "no_devices") {
                            NoDevicesItem(
                                modifier = Modifier.fillParentMaxSize(),
                                shouldShowHiddenDevices = visibleDevices.isEmpty() && allDevices.isNotEmpty(),
                                onAddDevice = onAddDevice,
                                onShowHiddenDevices = onShowHiddenDevices
                            )
                        }
                    }
                } else {
                    if (isWLEDCaptivePortal) {
                        item(key = "captive_portal") {
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
                            currentTime = currentTime,
                            onItemClick = onItemClick,
                            onItemEdit = onItemEdit,
                            viewModel = viewModel
                        )
                    } else {
                        allDevicesList(
                            devices = visibleDevices,
                            selectedDevice = selectedDevice,
                            currentTime = currentTime,
                            onItemClick = onItemClick,
                            onItemEdit = onItemEdit,
                            viewModel = viewModel
                        )
                    }

                    // This spacer is so that the last item of the list can be scrolled a bit
                    // further than just the bottom of the screen.
                    item(key = "end_spacer_buffer") {
                        Spacer(Modifier.padding(42.dp))
                    }
                }
            }

            // This should help prevent weird scrolling when devices switches between online and
            // offline state. This keeps the scroll position exactly as it is currently.
            // This is the suggested solution in https://issuetracker.google.com/issues/209652366#comment41
            SideEffect {
                if (!listState.isScrollInProgress) {
                    listState.requestScrollToItem(
                        index = listState.firstVisibleItemIndex,
                        scrollOffset = listState.firstVisibleItemScrollOffset
                    )
                }
            }
        }
    }
}

fun LazyListScope.allDevicesList(
    devices: List<DeviceWithState>,
    selectedDevice: DeviceWithState?,
    currentTime: Long,
    onItemClick: (DeviceWithState) -> Unit,
    onItemEdit: (DeviceWithState) -> Unit,
    viewModel: DeviceWebsocketListViewModel
) {
    itemsIndexed(
        devices, key = { _, device -> device.device.macAddress }) { _, device ->

        DeviceRow(
            device = device,
            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
            currentTime = currentTime,
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
    currentTime: Long,
    onItemClick: (DeviceWithState) -> Unit,
    onItemEdit: (DeviceWithState) -> Unit,
    viewModel: DeviceWebsocketListViewModel
) {
    itemsIndexed(
        onlineDevices, key = { _, device -> device.device.macAddress }) { _, device ->

        DeviceRow(
            device = device,
            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
            currentTime = currentTime,
            onClick = onItemClick,
            onEdit = onItemEdit,
            viewModel = viewModel
        )
    }
    if (offlineDevices.isNotEmpty()) {
        item(key = "offline_label") {
            Text(stringResource(R.string.offline_devices))
        }
        itemsIndexed(
            offlineDevices, key = { _, device -> device.device.macAddress }) { _, device ->

            DeviceRow(
                device = device,
                isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
                currentTime = currentTime,
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
    currentTime: Long = 0,
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
        currentTime = currentTime,
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

/**
 * Returns true if the device is offline and should be shown as such.
 *
 * This is to avoid devices jumping between online and offline constantly if the connection is
 * unstable.
 */
private fun shouldShowAsOffline(
    device: DeviceWithState, currentTime: Long
): Boolean {
    return !device.isOnline && currentTime - device.device.lastSeen >= DEVICE_OFFLINE_TIMEOUT_MS
}