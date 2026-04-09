package ca.cgagnier.wlednativeandroid.ui.homeScreen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.BuildConfig
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.util.openUriSafely

@Composable
internal fun DrawerContent(
    showHiddenDevices: Boolean,
    addDevice: () -> Unit,
    toggleShowHiddenDevices: () -> Unit,
    openSettings: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier.verticalScroll(scrollState),
    ) {
        DrawerHeader()
        DrawerMainActions(addDevice, showHiddenDevices, toggleShowHiddenDevices, openSettings)
        HorizontalDivider(modifier = Modifier.padding(12.dp))
        DrawerExternalLinks(uriHandler)
        Spacer(Modifier.height(24.dp))
        DrawerVersionInfo()
    }
}

@Composable
private fun DrawerHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(id = R.drawable.wled_logo_akemi),
            contentDescription = stringResource(R.string.app_logo),
        )
    }
}

@Composable
private fun DrawerMainActions(
    addDevice: () -> Unit,
    showHiddenDevices: Boolean,
    toggleShowHiddenDevices: () -> Unit,
    openSettings: () -> Unit,
) {
    NavigationDrawerItem(
        label = { Text(text = stringResource(R.string.add_a_device)) },
        icon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_a_device),
            )
        },
        selected = false,
        onClick = addDevice,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
    ToggleHiddenDeviceButton(showHiddenDevices, toggleShowHiddenDevices)
    NavigationDrawerItem(
        label = { Text(text = stringResource(R.string.settings)) },
        icon = {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        },
        selected = false,
        onClick = openSettings,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun DrawerExternalLinks(uriHandler: UriHandler) {
    NavigationDrawerItem(
        label = { Text(text = stringResource(R.string.help)) },
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.baseline_help_24),
                contentDescription = stringResource(R.string.help),
            )
        },
        selected = false,
        onClick = { uriHandler.openUriSafely("https://kno.wled.ge/") },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
    NavigationDrawerItem(
        label = { Text(text = stringResource(R.string.support_me)) },
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.baseline_coffee_24),
                contentDescription = stringResource(R.string.support_me),
            )
        },
        selected = false,
        onClick = { uriHandler.openUriSafely("https://github.com/sponsors/Moustachauve") },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}

@Composable
private fun DrawerVersionInfo() {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val debugString =
            if (BuildConfig.BUILD_TYPE != "release") " - ${BuildConfig.BUILD_TYPE}" else ""
        Text(
            "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})$debugString",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            BuildConfig.APPLICATION_ID,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun ToggleHiddenDeviceButton(showHiddenDevices: Boolean, toggleShowHiddenDevices: () -> Unit) {
    val hiddenDeviceText = stringResource(
        if (showHiddenDevices) {
            R.string.hide_hidden_devices
        } else {
            R.string.show_hidden_devices
        },
    )
    val hiddenDeviceIcon = painterResource(
        if (showHiddenDevices) {
            R.drawable.ic_baseline_visibility_off_24
        } else {
            R.drawable.baseline_visibility_24
        },
    )
    NavigationDrawerItem(
        label = { Text(text = hiddenDeviceText) },
        icon = {
            Icon(
                painter = hiddenDeviceIcon,
                contentDescription = stringResource(R.string.show_hidden_devices),
            )
        },
        selected = false,
        onClick = toggleShowHiddenDevices,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
    )
}
