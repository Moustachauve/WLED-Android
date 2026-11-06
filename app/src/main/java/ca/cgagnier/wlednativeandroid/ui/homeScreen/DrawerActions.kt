package ca.cgagnier.wlednativeandroid.ui.homeScreen

data class DrawerActions(
    val addDevice: () -> Unit,
    val toggleShowHiddenDevices: () -> Unit,
    val openSettings: () -> Unit,
    val openSoundSync: () -> Unit,
    val openChangelogs: () -> Unit,
)
