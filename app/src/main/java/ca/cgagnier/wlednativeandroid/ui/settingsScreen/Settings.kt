package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings

@Composable
fun Settings(
    navigateUp: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            DeviceSettingsAppBar(
                navigateUp = navigateUp,
            )
        },
    ) { innerPadding ->
        Column (
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OneOrTwoColumnLayout(
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .widthIn(0.dp, 1100.dp),
                firstColumn = {
                    ListingOptions(
                        showHiddenDevices = settingsState.showHiddenDevices,
                        isAutoDiscoveryEnabled = settingsState.isAutoDiscoveryEnabled,
                        showOfflineDevicesLast = settingsState.showOfflineLast,
                        setShowHiddenDevices = {
                            viewModel.setShowHiddenDevices(it)
                        },
                        setAutoDiscover = {
                            viewModel.setAutoDiscover(it)
                        },
                        setShowOfflineDevicesLast = {
                            viewModel.setShowOfflineDevicesLast(it)
                        }
                    )
                },
                secondColumn = {
                    ThemeOptions(
                        currentTheme = settingsState.theme,
                        setTheme = {
                            viewModel.setTheme(it)
                        }
                    )
                }
            )
            //}
        }
    }
}

@Composable
fun DeviceSettingsAppBar(
    modifier: Modifier = Modifier,
    navigateUp: () -> Unit,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = {
            Text(stringResource(R.string.settings))
        },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.description_back_button)
                )
            }
        }
    )
}

@Composable
fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun RadioRow(
    label: String,
    checked: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelected() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = checked,
            onClick = onSelected,
        )
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ListingOptions(
    showHiddenDevices: Boolean,
    isAutoDiscoveryEnabled: Boolean,
    showOfflineDevicesLast: Boolean,
    setShowHiddenDevices: (Boolean) -> Unit,
    setAutoDiscover: (Boolean) -> Unit,
    setShowOfflineDevicesLast: (Boolean) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.listing_options),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            SwitchRow(
                label = stringResource(R.string.show_hidden_devices),
                checked = showHiddenDevices,
                onCheckedChange = setShowHiddenDevices
            )
            SwitchRow(
                label = stringResource(R.string.automatically_discover_new_devices),
                checked = isAutoDiscoveryEnabled,
                onCheckedChange = setAutoDiscover
            )
            SwitchRow(
                label = stringResource(R.string.show_offline_devices_last),
                checked = showOfflineDevicesLast,
                onCheckedChange = setShowOfflineDevicesLast
            )
        }
    }
}

@Composable
fun ThemeOptions(
    currentTheme: ThemeSettings,
    setTheme: (ThemeSettings) -> Unit = {},
) {
    val themes = listOf(
        Pair(ThemeSettings.Dark, stringResource(R.string.dark)),
        Pair(ThemeSettings.Light, stringResource(R.string.light)),
        Pair(ThemeSettings.Auto, stringResource(R.string.system_default)),
    )
    Card(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            themes.forEach { theme ->
                RadioRow(
                    label = theme.second,
                    checked = currentTheme == theme.first,
                    onSelected = {
                        setTheme(theme.first)
                    }
                )
            }
        }
    }
}

@Composable
fun OneOrTwoColumnLayout(
    modifier: Modifier = Modifier,
    firstColumn: @Composable () -> Unit,
    secondColumn: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth < 800.dp) {
            Column(modifier) {
                firstColumn()
                secondColumn()
            }
        } else {
            Row(modifier.height(IntrinsicSize.Min)) {
                Column(modifier = Modifier.weight(1f)) {
                    firstColumn()
                }
                Column(modifier = Modifier.weight(1f)) {
                    secondColumn()
                }
            }
        }
    }
}