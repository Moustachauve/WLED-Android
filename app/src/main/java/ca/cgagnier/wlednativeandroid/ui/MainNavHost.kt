package ca.cgagnier.wlednativeandroid.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetail
import ca.cgagnier.wlednativeandroid.ui.settingsScreen.Settings
import kotlinx.serialization.Serializable

@Composable
fun MainNavHost(
    navController: NavHostController = rememberNavController(),
    startDeviceAddress: NavigationEvent? = null,
) {
    NavHost(
        navController = navController,
        startDestination = DeviceListDetailScreen,
    ) {
        composable<DeviceListDetailScreen> {
            DeviceListDetail(
                initialDeviceAddress = startDeviceAddress,
                openSettings = {
                    navController.navigate(SettingsScreen)
                },
            )
        }
        composable<SettingsScreen> {
            Settings(
                navigateUp = {
                    navController.navigateUp()
                },
            )
        }
    }
}

@Serializable
object DeviceListDetailScreen

@Serializable
object SettingsScreen
