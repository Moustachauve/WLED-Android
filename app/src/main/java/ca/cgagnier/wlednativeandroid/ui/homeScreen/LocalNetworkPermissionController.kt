package ca.cgagnier.wlednativeandroid.ui.homeScreen

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import ca.cgagnier.wlednativeandroid.service.hasLocalNetworkPermission

internal data class LocalNetworkPermissionController(val isDenied: Boolean, val requestLocalNetworkAccess: () -> Unit)

@Composable
internal fun rememberLocalNetworkPermissionController(
    onPermissionGranted: () -> Unit,
): LocalNetworkPermissionController {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) {
        return LocalNetworkPermissionController(isDenied = false, requestLocalNetworkAccess = {})
    }

    val context = LocalContext.current
    var hasLaunchedPermissionRequest by rememberSaveable { mutableStateOf(false) }
    var hasCompletedPermissionCheck by rememberSaveable { mutableStateOf(false) }
    var isPermissionDenied by rememberSaveable { mutableStateOf(false) }
    var isExplicitUserRetry by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        val wasUserRetry = isExplicitUserRetry
        isExplicitUserRetry = false
        hasCompletedPermissionCheck = true
        isPermissionDenied = !isGranted
        handlePermissionResult(
            context = context,
            isGranted = isGranted,
            wasUserRetry = wasUserRetry,
            onPermissionGranted = onPermissionGranted,
        )
    }

    LaunchedEffect(Unit) {
        if (!hasLaunchedPermissionRequest) {
            hasLaunchedPermissionRequest = true
            if (!context.hasLocalNetworkPermission()) {
                permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            } else {
                hasCompletedPermissionCheck = true
                isPermissionDenied = false
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        if (hasCompletedPermissionCheck) {
            val isGranted = context.hasLocalNetworkPermission()
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
        requestLocalNetworkAccess = {
            isExplicitUserRetry = true
            permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        },
    )
}

private fun handlePermissionResult(
    context: Context,
    isGranted: Boolean,
    wasUserRetry: Boolean,
    onPermissionGranted: () -> Unit,
) {
    if (isGranted) {
        onPermissionGranted()
    } else if (wasUserRetry && !context.shouldShowLocalNetworkRationale()) {
        openAppDetailsSettings(context)
    }
}

private fun Context.shouldShowLocalNetworkRationale(): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(
        activity,
        Manifest.permission.ACCESS_LOCAL_NETWORK,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun openAppDetailsSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}
