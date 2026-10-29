package ca.cgagnier.wlednativeandroid.ui.homeScreen.detail

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import ca.cgagnier.wlednativeandroid.ui.components.rememberSaveableWebViewState
import ca.cgagnier.wlednativeandroid.ui.components.rememberWebViewNavigator

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
                navigateUp = navigateUp,
                editItem = {
                    onItemEdit(device)
                },
                refreshPage = {
                    navigator.reload()
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 3.dp)
                .fillMaxSize()
                .clip(shape = MaterialTheme.shapes.large),
        ) {
            DeviceWebView(
                device.device,
                state = webViewState,
                navigator = navigator,
            )
            if (webViewState.loadingState is LoadingState.Loading && !webViewState.isError) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
fun DeviceDetailAppBar(
    device: DeviceWithState,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    refreshPage: () -> Unit,
    editItem: () -> Unit,
) {
    TopAppBar(
        title = {
            DeviceInfoTwoRows(device = device, nameMaxLines = 1)
        },
        navigationIcon = {
            AnimatedVisibility(visible = canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.description_back_button),
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = refreshPage) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.refresh_page),
                )
            }
            if (!device.isAPMode) {
                IconButton(onClick = editItem) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.edit_device),
                    )
                }
            }
        },
    )
}
