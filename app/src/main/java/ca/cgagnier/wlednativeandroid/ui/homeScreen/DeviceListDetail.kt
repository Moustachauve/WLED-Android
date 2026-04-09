package ca.cgagnier.wlednativeandroid.ui.homeScreen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.ui.NavigationEvent
import ca.cgagnier.wlednativeandroid.ui.homeScreen.detail.DeviceDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit.DeviceEdit
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceList
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod") // Navigator requires type inference, limiting extraction options
@Composable
fun DeviceListDetail(
    modifier: Modifier = Modifier,
    initialDeviceMacAddress: NavigationEvent? = null,
    openSettings: () -> Unit,
    viewModel: DeviceListDetailViewModel = hiltViewModel(),
    deviceWebsocketListViewModel: DeviceWebsocketListViewModel = hiltViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val defaultScaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val customScaffoldDirective = defaultScaffoldDirective.copy(
        horizontalPartitionSpacerSize = 0.dp,
    )
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Any>(scaffoldDirective = customScaffoldDirective)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    LaunchedEffect(initialDeviceMacAddress) {
        if (initialDeviceMacAddress != null) {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail,
                contentKey = initialDeviceMacAddress.macAddress,
            )
        }
    }

    val devices by deviceWebsocketListViewModel.allDevicesWithState.collectAsStateWithLifecycle()
    val selectedDeviceMacAddress = navigator.currentDestination?.contentKey as? String
    val selectedDevice = rememberSelectedDevice(devices, selectedDeviceMacAddress)

    val showHiddenDevices by viewModel.showHiddenDevices.collectAsStateWithLifecycle()
    val isWLEDCaptivePortal by viewModel.isWLEDCaptivePortal.collectAsStateWithLifecycle()
    val isAddDeviceDialogVisible by viewModel.isAddDeviceDialogVisible.collectAsStateWithLifecycle()

    val navigateToDetail: (DeviceWithState) -> Unit = { device ->
        coroutineScope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, device.device.macAddress)
        }
    }
    val navigateToEdit: (DeviceWithState) -> Unit = { device ->
        coroutineScope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Extra, device.device.macAddress)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                DrawerSheetContent(coroutineScope, drawerState, showHiddenDevices, viewModel, openSettings)
            }
        },
    ) {
        Scaffold { innerPadding ->
            NavigableListDetailPaneScaffold(
                modifier = modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .imePadding(),
                navigator = navigator,
                defaultBackBehavior = BackNavigationBehavior.PopUntilScaffoldValueChange,
                listPane = {
                    AnimatedPane {
                        DeviceList(
                            selectedDevice,
                            isWLEDCaptivePortal = isWLEDCaptivePortal,
                            onItemClick = navigateToDetail,
                            onAddDevice = { viewModel.showAddDeviceDialog() },
                            onShowHiddenDevices = { viewModel.toggleShowHiddenDevices() },
                            onRefresh = { viewModel.startDiscoveryServiceTimed() },
                            onItemEdit = {
                                navigateToDetail(it)
                                navigateToEdit(it)
                            },
                            onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                        )
                    }
                },
                detailPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            DeviceDetail(
                                device = device,
                                onItemEdit = { navigateToEdit(device) },
                                canNavigateBack = navigator.canNavigateBack(),
                                navigateUp = { coroutineScope.launch { navigator.navigateBack() } },
                            )
                        } ?: SelectDeviceView()
                    }
                },
                extraPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            DeviceEdit(
                                device = device,
                                canNavigateBack = navigator.canNavigateBack(),
                                navigateUp = { coroutineScope.launch { navigator.navigateBack() } },
                            )
                        }
                    }
                },
            )
        }

        BackHandler(enabled = drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        }
    }

    if (isAddDeviceDialogVisible) {
        DeviceAdd(onDismissRequest = { viewModel.hideAddDeviceDialog() })
    }
}

@Composable
private fun rememberSelectedDevice(
    devices: List<DeviceWithState>,
    selectedDeviceMacAddress: String?,
): DeviceWithState? {
    return remember(devices, selectedDeviceMacAddress) {
        if (selectedDeviceMacAddress == AP_MODE_MAC_ADDRESS) {
            return@remember getApModeDeviceWithState()
        }
        devices.firstOrNull { it.device.macAddress == selectedDeviceMacAddress }
    }
}

@Composable
private fun DrawerSheetContent(
    coroutineScope: CoroutineScope,
    drawerState: DrawerState,
    showHiddenDevices: Boolean,
    viewModel: DeviceListDetailViewModel,
    openSettings: () -> Unit,
) {
    DrawerContent(
        showHiddenDevices = showHiddenDevices,
        addDevice = {
            coroutineScope.launch {
                viewModel.showAddDeviceDialog()
                drawerState.close()
            }
        },
        toggleShowHiddenDevices = {
            coroutineScope.launch {
                viewModel.toggleShowHiddenDevices()
                drawerState.close()
            }
        },
        openSettings = {
            coroutineScope.launch {
                openSettings()
                drawerState.close()
            }
        },
    )
}
