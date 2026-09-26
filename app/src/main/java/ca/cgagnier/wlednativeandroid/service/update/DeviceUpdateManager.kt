package ca.cgagnier.wlednativeandroid.service.update

import android.util.Log
import ca.cgagnier.wlednativeandroid.model.Branch
import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.DeviceStateInfo
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import javax.inject.Inject

private const val TAG = "DeviceUpdateManager"

class DeviceUpdateManager @Inject constructor(private val releaseService: ReleaseService) {

    /**
     * Checks if a software update is available for the given [device] and [stateInfo].
     *
     * @return The latest release version tag (e.g., "0.14.0") if an update is available,
     * or `null` if up-to-date, OTA is disabled, or state info is absent.
     */
    suspend fun checkForUpdate(device: Device, stateInfo: DeviceStateInfo?): String? {
        val info = stateInfo?.info ?: return null
        val repository = getRepositoryFromInfo(info)
        Log.d(
            TAG,
            "Checking for software update for ${device.macAddress} on $repository",
        )
        return releaseService.getNewerReleaseTag(
            deviceInfo = info,
            branch = device.branch,
            ignoreVersion = device.skipUpdateTag,
        )
    }

    /**
     * Convenience method to check for updates given an immutable [DeviceWithState].
     */
    suspend fun checkForUpdate(deviceWithState: DeviceWithState): String? =
        checkForUpdate(deviceWithState.device, deviceWithState.stateInfo)

    /**
     * Returns a [Flow] that emits the version tag (e.g., "0.14.0") if an update is available,
     * or null if up-to-date, reacting to emissions from [deviceWithStateFlow].
     *
     * Deduplicates checks using [UpdateCheckKey] to prevent redundant network/database calls
     * when high-frequency state updates (e.g., brightness or uptime changes) occur.
     */
    fun getUpdateFlow(deviceWithStateFlow: Flow<DeviceWithState>): Flow<String?> = deviceWithStateFlow
        .distinctUntilChangedBy { deviceWithState ->
            UpdateCheckKey(
                macAddress = deviceWithState.device.macAddress,
                version = deviceWithState.stateInfo?.info?.version,
                options = deviceWithState.stateInfo?.info?.options,
                brand = deviceWithState.stateInfo?.info?.brand,
                product = deviceWithState.stateInfo?.info?.product,
                repository = deviceWithState.stateInfo?.info?.repository,
                branch = deviceWithState.device.branch,
                skipUpdateTag = deviceWithState.device.skipUpdateTag,
            )
        }
        .map { deviceWithState ->
            checkForUpdate(deviceWithState)
        }

    private data class UpdateCheckKey(
        val macAddress: String,
        val version: String?,
        val options: Int?,
        val brand: String?,
        val product: String?,
        val repository: String?,
        val branch: Branch,
        val skipUpdateTag: String,
    )
}
