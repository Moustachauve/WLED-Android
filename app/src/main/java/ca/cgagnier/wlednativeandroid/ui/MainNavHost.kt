package ca.cgagnier.wlednativeandroid.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetailActions
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.Settings
import kotlinx.serialization.Serializable

@Serializable
data object DeviceListDetailKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Composable
fun MainNavHost(
    backStack: NavBackStack<NavKey> = rememberNavBackStack(DeviceListDetailKey),
    startDeviceAddress: NavigationEvent? = null,
    openChangelogs: () -> Unit = {},
) {
    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeAt(backStack.lastIndex)
            }
        },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<DeviceListDetailKey> {
                DeviceListDetail(
                    initialDeviceMacAddress = startDeviceAddress,
                    actions = DeviceListDetailActions(
                        openSettings = {
                            backStack.add(SettingsKey)
                        },
                        openChangelogs = openChangelogs,
                    ),
                )
            }
            entry<SettingsKey> {
                Settings(
                    navigateUp = {
                        if (backStack.size > 1) {
                            backStack.removeAt(backStack.lastIndex)
                        }
                    },
                )
            }
        },
    )
}
