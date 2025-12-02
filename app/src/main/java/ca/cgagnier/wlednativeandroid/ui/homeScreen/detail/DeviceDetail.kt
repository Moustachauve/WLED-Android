package ca.cgagnier.wlednativeandroid.ui.homeScreen.detail

import android.annotation.SuppressLint
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.components.DeviceInfoTwoRows
import ca.cgagnier.wlednativeandroid.ui.components.DeviceWebView
import ca.cgagnier.wlednativeandroid.ui.components.LoadingState
import ca.cgagnier.wlednativeandroid.ui.components.WebViewState
import ca.cgagnier.wlednativeandroid.ui.components.rememberSaveableWebViewState
import ca.cgagnier.wlednativeandroid.ui.components.rememberWebViewNavigator

private const val TAG = "ui.DeviceDetail"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DeviceDetail(
    device: DeviceWithState,
    onItemEdit: (DeviceWithState) -> Unit,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
) {
    val webViewState = rememberSaveableWebViewState()
    val navigator = rememberWebViewNavigator()
    Scaffold(
        topBar = {
            DeviceDetailAppBar(
                device = device,
                canNavigateBack = canNavigateBack,
                webViewState = webViewState,
                navigateUp = navigateUp,
                editItem = {
                    onItemEdit(device)
                },
                refreshPage = {
                    navigator.reload()
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 3.dp)
                .fillMaxSize()
                .clip(shape = MaterialTheme.shapes.large),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LaunchedEffect(navigator) {
                val bundle = webViewState.viewState
                if (bundle == null) {
                    Log.i(TAG, "Loading device for first time")
                    // This is the first time load, so load the home page.
                    //navigator.loadUrl(device.getDeviceUrl())
                }
            }
            DeviceWebView(
                device.device,
                state = webViewState,
                navigator = navigator
            )
        }
    }
}

@Composable
fun DeviceDetailAppBar(
    device: DeviceWithState,
    canNavigateBack: Boolean,
    webViewState: WebViewState,
    navigateUp: () -> Unit,
    refreshPage: () -> Unit,
    editItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    DeviceInfoTwoRows(device = device, nameMaxLines = 1)
                }
                if (webViewState.loadingState is LoadingState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .height(22.dp)
                            .width(22.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        modifier = modifier,
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.description_back_button)
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = refreshPage) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.refresh_page)
                )
            }
            if (!device.isAPMode) {
                IconButton(onClick = editItem) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_device)
                    )
                }
            }
        }
    )
}