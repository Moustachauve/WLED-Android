package ca.cgagnier.wlednativeandroid.ui.homeScreen.deviceEdit

import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Repository
import ca.cgagnier.wlednativeandroid.model.VersionWithAssets

/**
 * Immutable snapshot of the device edit screen's UI state.
 *
 * Decoupled from the ViewModel so that composables can be
 * rendered in previews without any ViewModel dependency.
 */
data class DeviceEditUiState(
    val updateTag: String? = null,
    val isCheckingUpdates: Boolean = false,
    val repository: Repository? = null,
    val updateDetailsVersion: VersionWithAssets? = null,
    val updateDisclaimerVersion: VersionWithAssets? = null,
    val updateInstallVersion: VersionWithAssets? = null,
)

/**
 * All user-triggered actions on the device edit screen.
 *
 * Every callback defaults to a no-op, which makes previews trivial:
 * just use [DeviceEditActions] with no arguments.
 */
data class DeviceEditActions(
    val onCustomNameChange: (String) -> Unit = {},
    val onDeviceHiddenChange: (Boolean) -> Unit = {},
    val onBranchChange: (Branch) -> Unit = {},
    val onCheckForUpdates: () -> Unit = {},
    val onSeeUpdateDetails: (String) -> Unit = {},
    val onHideUpdateDetails: () -> Unit = {},
    val onSkipUpdate: (VersionWithAssets) -> Unit = {},
    val onInstallUpdate: (VersionWithAssets) -> Unit = {},
    val onHideUpdateDisclaimer: () -> Unit = {},
    val onAcceptUpdateDisclaimer: (VersionWithAssets) -> Unit = {},
    val onInstallFinished: (Boolean) -> Unit = {},
)
