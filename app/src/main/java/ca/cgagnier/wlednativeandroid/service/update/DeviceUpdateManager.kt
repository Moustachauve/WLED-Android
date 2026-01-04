package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import androidx.compose.runtime.snapshotFlow
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val TAG = "DeviceUpdateManager"

class DeviceUpdateManager @Inject constructor(
    private val releaseService: ReleaseService
) {

    /**
     * Returns a Flow that emits the version tag (e.g., "v0.14.0") if an update is available,
     * or null if up-to-date.
     */
    fun getUpdateFlow(deviceWithState: DeviceWithState): Flow<String?> {
        return snapshotFlow {
            // Create a stable key containing ONLY what matters for an update check
            val info = deviceWithState.stateInfo.value?.info
            val branch = deviceWithState.device.branch
            val skipTag = deviceWithState.device.skipUpdateTag
            Triple(info, branch, skipTag)
        }
            .distinctUntilChanged()
            .map { (info, branch, skipUpdateTag) ->
                if (info == null) return@map null

                val source = UpdateSourceRegistry.getSource(info) ?: return@map null
                Log.d(
                    TAG,
                    "Checking for software update for ${deviceWithState.device.macAddress} on ${source.githubOwner}:${source.githubRepo}"
                )
                releaseService.getNewerReleaseTag(
                    deviceInfo = info,
                    branch = branch,
                    ignoreVersion = skipUpdateTag,
                    updateSourceDefinition = source,
                )
            }
    }
}