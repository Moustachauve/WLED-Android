package ca.cgagnier.wlednativeandroid.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.components.ChangelogBottomSheet
import ca.cgagnier.wlednativeandroid.ui.theme.WLEDNativeTheme
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModel()
    private val changelogViewModel: ChangelogViewModel by viewModel()

    private var navigationEvent by mutableStateOf<NavigationEvent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        viewModel.handleIntent(intent)

        setContent {
            WLEDNativeTheme {
                DeepLinkStateHandler(
                    viewModel = viewModel,
                    onNavigate = { event ->
                        navigationEvent = event
                    },
                )
                MainNavHost(
                    startDeviceAddress = navigationEvent,
                    openChangelogs = { changelogViewModel.showAllChangelogs() },
                )
                ChangelogBottomSheet(viewModel = changelogViewModel)
            }
        }
        viewModel.downloadUpdateMetadata()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleIntent(intent)
    }

    companion object {
        const val EXTRA_DEVICE_MAC_ADDRESS = "device_mac_address"
    }
}

@Composable
private fun DeepLinkStateHandler(viewModel: MainViewModel, onNavigate: (NavigationEvent) -> Unit) {
    val deepLinkState by viewModel.deepLinkState.collectAsStateWithLifecycle()

    // Handle navigation when device is found
    LaunchedEffect(deepLinkState) {
        when (val state = deepLinkState) {
            is DeepLinkState.NavigateToDevice -> {
                onNavigate(state.event)
                viewModel.clearDeepLinkState()
            }

            else -> { /* Handled by dialogs below */ }
        }
    }

    // Show loading dialog when discovering device
    when (val state = deepLinkState) {
        is DeepLinkState.Loading -> {
            DeepLinkLoadingDialog(
                onDismiss = { viewModel.cancelDeepLink() },
            )
        }

        is DeepLinkState.Error -> {
            DeepLinkErrorDialog(
                message = stringResource(state.messageResId, state.address),
                onDismiss = { viewModel.clearDeepLinkState() },
            )
        }

        else -> { /* Idle or NavigateToDevice - no dialog needed */ }
    }
}

@Composable
private fun DeepLinkLoadingDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.deep_link_loading))
            }
        },
    )
}

@Composable
private fun DeepLinkErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        },
        title = { Text(stringResource(R.string.deep_link_error_title)) },
        text = { Text(message) },
    )
}

@Preview(showBackground = true)
@Composable
private fun DeepLinkLoadingDialogPreview() {
    DeepLinkLoadingDialog(onDismiss = {})
}

@Preview(showBackground = true)
@Composable
private fun DeepLinkErrorDialogPreview() {
    DeepLinkErrorDialog(
        message = "Could not reach device at 192.168.1.50. Make sure it is powered on and connected.",
        onDismiss = {},
    )
}

data class NavigationEvent(val macAddress: String, val id: String = java.util.UUID.randomUUID().toString())
