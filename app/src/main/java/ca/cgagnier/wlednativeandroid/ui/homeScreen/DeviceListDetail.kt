package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.ui.NavigationEvent
import ca.cgagnier.wlednativeandroid.ui.homeScreen.detail.DeviceDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit.DeviceEdit
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceList
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceListActions
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Suppress("LongMethod") // Navigator requires type inference, limiting extraction options
@Composable
fun DeviceListDetail(
    modifier: Modifier = Modifier,
    initialDeviceMacAddress: NavigationEvent? = null,
    actions: DeviceListDetailActions,
    viewModel: DeviceListDetailViewModel = koinViewModel(),
    deviceWebsocketListViewModel: DeviceWebsocketListViewModel = koinViewModel(),
) {
    val coroutineScope = rememberCoroutineScope()
    val defaultScaffoldDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfo())
    val customScaffoldDirective = defaultScaffoldDirective.copy(
        horizontalPartitionSpacerSize = 0.dp,
    )
    val navigator =
        rememberListDetailPaneScaffoldNavigator<Any>(scaffoldDirective = customScaffoldDirective)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val localNetworkPermission = rememberLocalNetworkPermissionController(
        onPermissionGranted = {
            deviceWebsocketListViewModel.refreshOfflineDevices()
            viewModel.onLocalNetworkPermissionGranted()
        },
    )

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
                DrawerSheetContent(
                    coroutineScope = coroutineScope,
                    drawerState = drawerState,
                    showHiddenDevices = showHiddenDevices,
                    viewModel = viewModel,
                    actions = actions,
                )
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
                            selectedDevice = selectedDevice,
                            isWLEDCaptivePortal = isWLEDCaptivePortal,
                            isLocalNetworkDenied = localNetworkPermission.isDenied,
                            actions = DeviceListActions(
                                onItemClick = navigateToDetail,
                                onAddDevice = { viewModel.showAddDeviceDialog() },
                                onShowHiddenDevices = { viewModel.toggleShowHiddenDevices() },
                                onOpenLocalNetworkSettings = localNetworkPermission.requestOrOpenSettings,
                                onRefresh = { viewModel.startDiscoveryServiceTimed() },
                                onItemEdit = {
                                    navigateToDetail(it)
                                    navigateToEdit(it)
                                },
                                onOpenDrawer = { coroutineScope.launch { drawerState.open() } },
                            ),
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

private data class LocalNetworkPermissionController(val isDenied: Boolean, val requestOrOpenSettings: () -> Unit)

@Composable
private fun rememberLocalNetworkPermissionController(
    onPermissionGranted: () -> Unit,
): LocalNetworkPermissionController {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
        return LocalNetworkPermissionController(isDenied = false, requestOrOpenSettings = {})
    }

    val context = LocalContext.current
    var hasLaunchedPermissionRequest by rememberSaveable { mutableStateOf(false) }
    var hasCompletedPermissionCheck by rememberSaveable { mutableStateOf(false) }
    var isPermissionDenied by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasCompletedPermissionCheck = true
        isPermissionDenied = !isGranted
        if (isGranted) {
            onPermissionGranted()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLaunchedPermissionRequest) {
            hasLaunchedPermissionRequest = true
            if (!isLocalNetworkPermissionGranted(context)) {
                permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                hasCompletedPermissionCheck = true
                isPermissionDenied = false
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        if (hasCompletedPermissionCheck) {
            val isGranted = isLocalNetworkPermissionGranted(context)
            val wasDenied = isPermissionDenied
            isPermissionDenied = !isGranted
            if (isGranted && wasDenied) {
                onPermissionGranted()
            }
        }
        onPauseOrDispose {}
    }

    return LocalNetworkPermissionController(
        isDenied = isPermissionDenied,
        requestOrOpenSettings = {
            val activity = context.findActivity()
            if (activity != null &&
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_LOCAL_NETWORK,
                )
            ) {
                permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                openAppDetailsSettings(context)
            }
        },
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isLocalNetworkPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_LOCAL_NETWORK,
        ) == PackageManager.PERMISSION_GRANTED

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
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
    actions: DeviceListDetailActions,
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
                actions.openSettings()
                drawerState.close()
            }
        },
        openChangelogs = {
            coroutineScope.launch {
                actions.openChangelogs()
                drawerState.close()
            }
        },
    )
}

data class DeviceListDetailActions(val openSettings: () -> Unit, val openChangelogs: () -> Unit = {})
