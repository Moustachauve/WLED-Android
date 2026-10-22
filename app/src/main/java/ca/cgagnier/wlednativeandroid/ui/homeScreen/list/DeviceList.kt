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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.PartitionedDevices
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.partitionDevices
import ca.cgagnier.wlednativeandroid.ui.components.DeviceInfoTwoRows
import ca.cgagnier.wlednativeandroid.ui.theme.DeviceTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val TAG = "screen_DeviceList"

data class DeviceListState(
    val allDevices: List<DeviceWithState>,
    val selectedDevice: DeviceWithState? = null,
    val isWLEDCaptivePortal: Boolean = false,
    val showOfflineDevicesLast: Boolean = true,
    val showHiddenDevices: Boolean = false,
    val isInitialLoading: Boolean = false,
    val currentTime: Long = 0L,
)

data class DeviceListActions(
    val onItemClick: (DeviceWithState) -> Unit = {},
    val onItemEdit: (DeviceWithState) -> Unit = {},
    val onAddDevice: () -> Unit = {},
    val onShowHiddenDevices: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenDrawer: () -> Unit = {},
    val onPowerSwitchToggle: (DeviceWithState, Boolean) -> Unit = { _, _ -> },
    val onBrightnessChanged: (DeviceWithState, Int) -> Unit = { _, _ -> },
    val onDeleteDevice: (Device) -> Unit = {},
)

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
    viewModel: DeviceWebsocketListViewModel = koinViewModel(),
) {
    val allDevices by viewModel.allDevicesWithState.collectAsStateWithLifecycle()
    val showOfflineDevicesLast by viewModel.showOfflineDevicesLast.collectAsStateWithLifecycle()
    val showHiddenDevices by viewModel.showHiddenDevices.collectAsStateWithLifecycle()

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

    DeviceListContent(
        state = DeviceListState(
            allDevices = allDevices,
            selectedDevice = selectedDevice,
            isWLEDCaptivePortal = isWLEDCaptivePortal,
            showOfflineDevicesLast = showOfflineDevicesLast,
            showHiddenDevices = showHiddenDevices,
            isInitialLoading = isInitialLoading,
            currentTime = currentTime,
        ),
        actions = DeviceListActions(
            onItemClick = onItemClick,
            onItemEdit = onItemEdit,
            onAddDevice = onAddDevice,
            onShowHiddenDevices = onShowHiddenDevices,
            onRefresh = {
                onRefresh()
                viewModel.refreshOfflineDevices()
            },
            onOpenDrawer = onOpenDrawer,
            onPowerSwitchToggle = viewModel::setDevicePower,
            onBrightnessChanged = viewModel::setBrightness,
            onDeleteDevice = viewModel::deleteDevice,
        ),
    )
}

@Composable
fun DeviceListContent(state: DeviceListState, actions: DeviceListActions = DeviceListActions()) {
    val listState = rememberLazyListState()

    val partitioned = remember(state.allDevices, state.showHiddenDevices, state.currentTime) {
        partitionDevices(
            devices = state.allDevices,
            showHiddenDevices = state.showHiddenDevices,
            currentTimeMillis = state.currentTime,
        )
    }

    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val refresh: () -> Unit = {
        isRefreshing = true
        actions.onRefresh()
        coroutineScope.launch {
            delay(1800)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            DeviceListAppBar(
                onOpenDrawer = actions.onOpenDrawer,
                onAddDevice = actions.onAddDevice,
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
                deviceListItems(
                    state = state,
                    partitioned = partitioned,
                    actions = actions,
                )
            }

            // This should help prevent weird scrolling when devices switches between online and
            // offline state. This keeps the scroll position exactly as it is currently.
            // This is the suggested solution in https://issuetracker.google.com/issues/209652366#comment41
            SideEffect {
                if (!listState.isScrollInProgress) {
                    listState.requestScrollToItem(
                        index = listState.firstVisibleItemIndex,
                        scrollOffset = listState.firstVisibleItemScrollOffset,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.deviceListItems(
    state: DeviceListState,
    partitioned: PartitionedDevices,
    actions: DeviceListActions,
) {
    // First invisible item to keep the scroll at the top if the list changes under it.
    // This is a "hack" suggested here: https://issuetracker.google.com/issues/234223556#comment2
    item(key = "first_invisible") {
        Spacer(
            Modifier
                .padding(1.dp)
                .height(0.dp),
        )
    }
    if (partitioned.visible.isEmpty() && !state.isWLEDCaptivePortal) {
        // Don't show the empty page during the initial load to improve the user
        // experience.
        if (state.isInitialLoading) {
            items(3) {
                SkeletonDeviceRow()
            }
        } else {
            item(key = "no_devices") {
                NoDevicesItem(
                    modifier = Modifier.fillParentMaxSize(),
                    shouldShowHiddenDevices = partitioned.visible.isEmpty() && state.allDevices.isNotEmpty(),
                    onAddDevice = actions.onAddDevice,
                    onShowHiddenDevices = actions.onShowHiddenDevices,
                )
            }
        }
    } else {
        if (state.isWLEDCaptivePortal) {
            item(key = "captive_portal") {
                val device = getApModeDeviceWithState()
                DeviceAPListItem(
                    isSelected = device.device.macAddress == state.selectedDevice?.device?.macAddress,
                    onClick = { actions.onItemClick(device) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        if (state.showOfflineDevicesLast) {
            onlineOfflineDevicesList(
                onlineDevices = partitioned.online,
                offlineDevices = partitioned.offline,
                selectedDevice = state.selectedDevice,
                currentTime = state.currentTime,
                actions = actions,
            )
        } else {
            allDevicesList(
                devices = partitioned.visible,
                selectedDevice = state.selectedDevice,
                currentTime = state.currentTime,
                actions = actions,
            )
        }

        // This spacer is so that the last item of the list can be scrolled a bit
        // further than just the bottom of the screen.
        item(key = "end_spacer_buffer") {
            Spacer(Modifier.padding(42.dp))
        }
    }
}

fun LazyListScope.allDevicesList(
    devices: List<DeviceWithState>,
    selectedDevice: DeviceWithState?,
    currentTime: Long,
    actions: DeviceListActions,
) {
    itemsIndexed(
        devices,
        key = { _, device -> device.device.macAddress },
    ) { _, device ->

        DeviceRow(
            device = device,
            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
            currentTime = currentTime,
            actions = actions,
        )
    }
}

fun LazyListScope.onlineOfflineDevicesList(
    onlineDevices: List<DeviceWithState>,
    offlineDevices: List<DeviceWithState>,
    selectedDevice: DeviceWithState?,
    currentTime: Long,
    actions: DeviceListActions,
) {
    itemsIndexed(
        onlineDevices,
        key = { _, device -> device.device.macAddress },
    ) { _, device ->

        DeviceRow(
            device = device,
            isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
            currentTime = currentTime,
            actions = actions,
        )
    }
    if (offlineDevices.isNotEmpty()) {
        item(key = "offline_label") {
            Text(stringResource(R.string.offline_devices))
        }
        itemsIndexed(
            offlineDevices,
            key = { _, device -> device.device.macAddress },
        ) { _, device ->

            DeviceRow(
                device = device,
                isSelected = device.device.macAddress == selectedDevice?.device?.macAddress,
                currentTime = currentTime,
                actions = actions,
            )
        }
    }
}

@Composable
fun LazyItemScope.DeviceRow(
    device: DeviceWithState,
    isSelected: Boolean,
    currentTime: Long = 0,
    actions: DeviceListActions,
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
        onClick = { actions.onItemClick(device) },
        swipeToDismissBoxState = swipeDismissState,
        onDismiss = { direction ->
            if (direction == SwipeToDismissBoxValue.EndToStart) {
                isConfirmingDelete = true
            } else if (direction == SwipeToDismissBoxValue.StartToEnd) {
                coroutineScope.launch {
                    swipeDismissState.reset()
                    actions.onItemEdit(device)
                }
            }
        },
        onPowerSwitchToggle = { isOn ->
            actions.onPowerSwitchToggle(device, isOn)
        },
        onBrightnessChanged = { brightness ->
            actions.onBrightnessChanged(device, brightness)
        },
        modifier = Modifier.animateItem(),
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
                actions.onDeleteDevice(device.device)
            }
        }, onDismiss = {
            isConfirmingDelete = false
        })
    }
}

@Composable
fun DeviceListAppBar(modifier: Modifier = Modifier, onOpenDrawer: () -> Unit, onAddDevice: () -> Unit) {
    CenterAlignedTopAppBar(modifier = modifier, title = {
        Image(
            painter = painterResource(id = R.drawable.wled_logo_akemi),
            contentDescription = stringResource(R.string.app_logo),
        )
    }, navigationIcon = {
        IconButton(onClick = onOpenDrawer) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.description_menu_button),
            )
        }
    }, actions = {
        IconButton(onClick = onAddDevice) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_a_device),
            )
        }
    })
}

@Composable
fun ConfirmDeleteDialog(device: DeviceWithState? = null, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    device?.let {
        AlertDialog(title = {
            Text(text = stringResource(R.string.deleting_device))
        }, text = {
            DeviceTheme(device) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    DeviceInfoTwoRows(
                        modifier = Modifier.padding(16.dp),
                        device = device,
                    )
                }
            }
        }, onDismissRequest = {
            onDismiss()
        }, confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                },
            ) {
                Text(
                    stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }, dismissButton = {
            TextButton(
                onClick = {
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.cancel))
            }
        })
    }
}
