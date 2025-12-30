package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.BuildConfig
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.model.AP_MODE_MAC_ADDRESS
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.service.websocket.getApModeDeviceWithState
import ca.cgagnier.wlednativeandroid.ui.homeScreen.detail.DeviceDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceAdd.DeviceAdd
import ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit.DeviceEdit
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceList
import ca.cgagnier.wlednativeandroid.ui.homeScreen.list.DeviceWebsocketListViewModel
import kotlinx.coroutines.launch

private const val TAG = "screen_DeviceListDetail"

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun DeviceListDetail(
    modifier: Modifier = Modifier,
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

    val devices by deviceWebsocketListViewModel.allDevicesWithState.collectAsStateWithLifecycle()
    val selectedDeviceMacAddress = navigator.currentDestination?.contentKey as? String
    // TODO: Move the selectedDevice to the ViewModel
    val selectedDevice = remember(devices, selectedDeviceMacAddress) {
        if (selectedDeviceMacAddress == AP_MODE_MAC_ADDRESS) {
            return@remember getApModeDeviceWithState()
        }
        devices.firstOrNull { it.device.macAddress == selectedDeviceMacAddress }
    }

    val showHiddenDevices by viewModel.showHiddenDevices.collectAsStateWithLifecycle()
    val isWLEDCaptivePortal by viewModel.isWLEDCaptivePortal.collectAsStateWithLifecycle()
    val isAddDeviceDialogVisible by viewModel.isAddDeviceDialogVisible.collectAsStateWithLifecycle()

    val addDevice = { viewModel.showAddDeviceDialog() }

    val navigateToDeviceDetail: (DeviceWithState) -> Unit = { device: DeviceWithState ->
        coroutineScope.launch {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Detail, contentKey = device.device.macAddress
            )
        }
    }


    val navigateToDeviceEdit = { device: DeviceWithState ->
        coroutineScope.launch {
            navigator.navigateTo(
                pane = ListDetailPaneScaffoldRole.Extra, contentKey = device.device.macAddress
            )
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState, gesturesEnabled = drawerState.isOpen, drawerContent = {
            ModalDrawerSheet {
                DrawerContent(showHiddenDevices = showHiddenDevices, addDevice = {
                    coroutineScope.launch {
                        addDevice()
                        drawerState.close()
                    }
                }, toggleShowHiddenDevices = {
                    coroutineScope.launch {
                        viewModel.toggleShowHiddenDevices()
                        drawerState.close()
                    }
                }, openSettings = {
                    coroutineScope.launch {
                        openSettings()
                        drawerState.close()
                    }
                })
            }
        }) {
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
                            onItemClick = navigateToDeviceDetail,
                            onAddDevice = addDevice,
                            onShowHiddenDevices = {
                                viewModel.toggleShowHiddenDevices()
                            },
                            onRefresh = {
                                viewModel.startDiscoveryServiceTimed()
                            },
                            onItemEdit = {
                                navigateToDeviceDetail(it)
                                navigateToDeviceEdit(it)
                            },
                            onOpenDrawer = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            })
                    }
                },
                detailPane = {
                    AnimatedPane {
                        SelectDeviceView()
                        selectedDevice?.let { device ->
                            DeviceDetail(device = device, onItemEdit = {
                                navigateToDeviceEdit(device)
                            }, canNavigateBack = navigator.canNavigateBack(), navigateUp = {
                                coroutineScope.launch {
                                    navigator.navigateBack()
                                }
                            })
                        } ?: SelectDeviceView()
                    }
                },
                extraPane = {
                    AnimatedPane {
                        selectedDevice?.let { device ->
                            DeviceEdit(
                                device = device,
                                canNavigateBack = navigator.canNavigateBack(),
                                navigateUp = {
                                    coroutineScope.launch {
                                        navigator.navigateBack()
                                    }
                                })
                        }
                    }
                })
        }

        /* Close drawer when back button is pressed. This is to fix a state that can happen when a
         * user navigates to another app with the drawer open and then navigates back to the app.
         * This would cause them to be stuck in the drawer and the back button would go to the
         * previous app instead of closing the drawer. */
        BackHandler(enabled = drawerState.isOpen) {
            coroutineScope.launch {
                drawerState.close()
            }
        }
    }


    if (isAddDeviceDialogVisible) {
        DeviceAdd(
            onDismissRequest = {
                viewModel.hideAddDeviceDialog()
            })
    }
}

@Composable
private fun DrawerContent(
    showHiddenDevices: Boolean,
    addDevice: () -> Unit,
    toggleShowHiddenDevices: () -> Unit,
    openSettings: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.verticalScroll(scrollState)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.wled_logo_akemi),
                contentDescription = stringResource(R.string.app_logo)
            )
        }
        NavigationDrawerItem(
            label = { Text(text = stringResource(R.string.add_a_device)) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_a_device)
                )
            },
            selected = false,
            onClick = addDevice,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        ToggleHiddenDeviceButton(showHiddenDevices, toggleShowHiddenDevices)
        NavigationDrawerItem(
            label = { Text(text = stringResource(R.string.settings)) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            },
            selected = false,
            onClick = openSettings,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        HorizontalDivider(modifier = Modifier.padding(12.dp))

        NavigationDrawerItem(
            label = { Text(text = stringResource(R.string.help)) }, icon = {
            Icon(
                painter = painterResource(id = R.drawable.baseline_help_24),
                contentDescription = stringResource(R.string.help)
            )
        }, selected = false, onClick = {
            uriHandler.openUriSafely("https://kno.wled.ge/")
        }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        NavigationDrawerItem(
            label = { Text(text = stringResource(R.string.support_me)) }, icon = {
            Icon(
                painter = painterResource(id = R.drawable.baseline_coffee_24),
                contentDescription = stringResource(R.string.support_me)
            )
        }, selected = false, onClick = {
            uriHandler.openUriSafely("https://github.com/sponsors/Moustachauve")
        }, modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val debugString =
                if (BuildConfig.BUILD_TYPE != "release") " - ${BuildConfig.BUILD_TYPE}" else ""
            Text(
                "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${debugString}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                BuildConfig.APPLICATION_ID, style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ToggleHiddenDeviceButton(
    showHiddenDevices: Boolean, toggleShowHiddenDevices: () -> Unit
) {
    val hiddenDeviceText = stringResource(
        if (showHiddenDevices) R.string.hide_hidden_devices
        else R.string.show_hidden_devices
    )
    val hiddenDeviceIcon = painterResource(
        if (showHiddenDevices) R.drawable.ic_baseline_visibility_off_24
        else R.drawable.baseline_visibility_24
    )
    NavigationDrawerItem(
        label = { Text(text = hiddenDeviceText) },
        icon = {
            Icon(
                painter = hiddenDeviceIcon,
                contentDescription = stringResource(R.string.show_hidden_devices)
            )
        },
        selected = false,
        onClick = toggleShowHiddenDevices,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}

@Composable
fun SelectDeviceView() {
    Card(
        modifier = Modifier.padding(top = TopAppBarDefaults.MediumAppBarCollapsedHeight),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(id = R.drawable.wled_logo_akemi),
                contentDescription = stringResource(R.string.app_logo)
            )
            Text(stringResource(R.string.select_a_device_from_the_list))
        }
    }
}

// TODO: Move this to a utility file or somewhere else, maybe.
/**
 * Open Uri in external browser and do error handling.
 *
 * Errors can happen if, for some reason, a user doesn't have any browser installed, for example.
 */
fun UriHandler.openUriSafely(uri: String) {
    try {
        this.openUri(uri)
    } catch (e: IllegalArgumentException) {
        // Log the error so you can see it in Crashlytics non-fatals if you use it
        Log.e(TAG, "No browser found to open: $uri", e)
    } catch (e: Exception) {
        // Catch generic exceptions just in case OEM implementations behave weirdly
        Log.e(TAG, "Error opening URI: $uri", e)
    }
}
