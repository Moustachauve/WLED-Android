package ca.cgagnier.wlednativeandroid.ui.homeScreen.update

data class UpdateInstallingState(
    val canDismiss: Boolean = false,
    val step: UpdateInstallingStep = UpdateInstallingStep.Installing,
    val assetName: String = ""
)

sealed class UpdateInstallingStep {
    data object Starting : UpdateInstallingStep()
    data class Downloading(val progress: Int) : UpdateInstallingStep()
    data object Installing : UpdateInstallingStep()
    data object NoCompatibleVersion : UpdateInstallingStep()
    data class Error(
        val error: String, val showError: Boolean = false
    ) : UpdateInstallingStep()

    data object Done : UpdateInstallingStep()
}